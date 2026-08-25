package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.config.PluginSettings;
import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.ParticipantProgress;
import cn.cloudfl4re.boatrace.model.ParticipantStatus;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.RacePhase;
import cn.cloudfl4re.boatrace.model.RaceResultEntry;
import cn.cloudfl4re.boatrace.model.RaceSession;
import cn.cloudfl4re.boatrace.model.RoutePenalty;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.StoredLocation;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.model.TrialRun;
import cn.cloudfl4re.boatrace.persistence.DatabaseService;
import cn.cloudfl4re.boatrace.persistence.TrialSaveResult;
import cn.cloudfl4re.boatrace.scheduler.SchedulerFacade;
import cn.cloudfl4re.boatrace.scheduler.TaskHandle;
import cn.cloudfl4re.boatrace.util.RaceCodeGenerator;
import cn.cloudfl4re.boatrace.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.FireworkEffect;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class RaceManager {
    private final Plugin plugin;
    private final TrackService tracks;
    private final LeaderboardService leaderboards;
    private final LastRaceService lastRaces;
    private final DatabaseService database;
    private final SchedulerFacade scheduler;
    private final MessageService messages;
    private final Supplier<PluginSettings> settings;
    private final ParticleRenderer particles;
    private final NamespacedKey boatKey;
    private final RaceCodeGenerator codeGenerator = new RaceCodeGenerator();
    private final ConcurrentHashMap<String, RaceSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionByTrack = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> sessionByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TrialRun> trials = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TaskHandle> uiTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, StoredLocation> stagingOrigins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, StartSlot> stagingAssignments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> stagingRemaining = new ConcurrentHashMap<>();
    private final Set<String> stagingFailures = ConcurrentHashMap.newKeySet();
    private final Set<UUID> stagedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> raceBoats = ConcurrentHashMap.newKeySet();
    private final Set<UUID> stagedCorrections = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, UUID> boatOwners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, RoutePenalty> routePenalties = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> exitMessageTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> idleParticleTimes = new ConcurrentHashMap<>();
    private final Set<String> endingSessions = ConcurrentHashMap.newKeySet();
    private volatile TaskHandle cleanupTask = TaskHandle.NOOP;

    public RaceManager(
        Plugin plugin,
        TrackService tracks,
        LeaderboardService leaderboards,
        LastRaceService lastRaces,
        DatabaseService database,
        SchedulerFacade scheduler,
        MessageService messages,
        Supplier<PluginSettings> settings,
        ParticleRenderer particles
    ) {
        this.plugin = plugin;
        this.tracks = tracks;
        this.leaderboards = leaderboards;
        this.lastRaces = lastRaces;
        this.database = database;
        this.scheduler = scheduler;
        this.messages = messages;
        this.settings = settings;
        this.particles = particles;
        this.boatKey = new NamespacedKey(plugin, "race_boat");
    }

    public void startCleanupTask() {
        cleanupTask.cancel();
        cleanupTask = scheduler.runGlobalRepeating(this::cleanupTick, settings.get().cleanupPeriodTicks(), settings.get().cleanupPeriodTicks());
    }

    public void cleanupStaleBoats(Set<UUID> boatIds) {
        for (UUID boatId : boatIds) {
            removeBoat(boatId);
        }
        database.clearOwnedBoats(boatIds).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear stale BoatRace boat records", failure);
            return null;
        });
    }

    public boolean isTrackInUse(String trackId) {
        return sessionByTrack.containsKey(trackId) || trials.values().stream().anyMatch(trial -> trial.trackId().equals(trackId));
    }

    public int trialCount(String trackId) {
        return (int) trials.values().stream().filter(trial -> trial.trackId().equals(trackId)).count();
    }

    public void createRoom(Player player) {
        UUID playerId = player.getUniqueId();
        if (sessionByPlayer.containsKey(playerId) || trials.containsKey(playerId)) {
            messages.send(player, "already-active");
            return;
        }
        Location location = player.getLocation();
        Optional<Track> found = tracks.atStart(location.getWorld().getUID(), point(location));
        if (found.isEmpty()) {
            messages.send(player, "room-not-at-start");
            return;
        }
        Track track = found.get();
        if (sessionByTrack.containsKey(track.id())) {
            messages.send(player, "room-track-busy");
            return;
        }
        int activeTrials = trialCount(track.id());
        if (activeTrials > 0) {
            messages.send(player, "room-trial-active", Map.of("count", String.valueOf(activeTrials)));
            return;
        }
        String code = codeGenerator.next(sessions::containsKey);
        long now = System.nanoTime();
        RaceSession session = new RaceSession(code, track.id(), playerId, track.slots().size(), System.currentTimeMillis(), now);
        if (!session.join(playerId, player.getName(), now) || sessionByTrack.putIfAbsent(track.id(), code) != null) {
            messages.send(player, "room-track-busy");
            return;
        }
        sessions.put(code, session);
        sessionByPlayer.put(playerId, code);
        plugin.getLogger().info("Race room " + code + " created for track " + track.id());
        messages.send(player, "room-created", Map.of("code", code, "track", track.id()));
    }

    public void joinRoom(Player player, String rawCode) {
        UUID playerId = player.getUniqueId();
        if (sessionByPlayer.containsKey(playerId) || trials.containsKey(playerId)) {
            messages.send(player, "already-active");
            return;
        }
        String code = rawCode.toUpperCase();
        RaceSession session = sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-found", Map.of("code", code));
            return;
        }
        if (session.phase() != RacePhase.WAITING) {
            messages.send(player, "room-not-waiting");
            return;
        }
        if (session.size() >= session.capacity()) {
            messages.send(player, "room-full");
            return;
        }
        if (!session.join(playerId, player.getName(), System.nanoTime())) {
            messages.send(player, "room-not-waiting");
            return;
        }
        sessionByPlayer.put(playerId, code);
        messages.send(player, "room-joined", Map.of("code", code));
        broadcast(session, "room-player-joined", Map.of("player", player.getName()), playerId);
    }

    public void leave(Player player) {
        UUID playerId = player.getUniqueId();
        routePenalties.remove(playerId);
        exitMessageTimes.remove(playerId);
        TrialRun trial = trials.remove(playerId);
        if (trial != null) {
            stopUi(playerId);
            messages.send(player, "room-left");
            return;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        RacePhase phase = session.phase();
        if (phase == RacePhase.WAITING) {
            session.removeWaiting(playerId, System.nanoTime());
            sessionByPlayer.remove(playerId, code);
            messages.send(player, "room-left");
            return;
        }
        if (phase == RacePhase.STAGING || phase == RacePhase.COUNTDOWN) {
            failStaging(code, "有玩家在发车前离开");
            session.removeWaiting(playerId, System.nanoTime());
            sessionByPlayer.remove(playerId, code);
            messages.send(player, "room-left");
            return;
        }
        if (phase == RacePhase.RUNNING) {
            markDnf(session, playerId, true);
            messages.send(player, "room-left");
            finishIfComplete(session);
            return;
        }
        messages.send(player, "room-left");
    }

    public void startByPlayer(Player player) {
        String code = sessionByPlayer.get(player.getUniqueId());
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        if (!session.ownerId().equals(player.getUniqueId()) && !player.hasPermission("boatrace.admin")) {
            messages.send(player, "room-owner-only");
            return;
        }
        startSession(session, player);
    }

    public void startByAdmin(CommandSender sender, String rawCode) {
        String code = rawCode.toUpperCase();
        RaceSession session = sessions.get(code);
        if (session == null) {
            messages.send(sender, "room-not-found", Map.of("code", code));
            return;
        }
        startSession(session, sender);
    }

    private void startSession(RaceSession session, CommandSender sender) {
        Track track = tracks.get(session.trackId()).orElse(null);
        if (track == null || session.phase() != RacePhase.WAITING || session.size() < 1 || session.size() > track.slots().size()) {
            messages.send(sender, "room-not-waiting");
            return;
        }
        List<ParticipantProgress> participants = session.participants().stream()
            .sorted(Comparator.comparingInt(ParticipantProgress::joinOrder))
            .toList();
        if (participants.stream().anyMatch(value -> Bukkit.getPlayer(value.playerId()) == null)) {
            messages.send(sender, "room-owner-offline");
            return;
        }
        if (!session.beginStaging(System.nanoTime())) {
            messages.send(sender, "room-not-waiting");
            return;
        }
        session.touch(System.nanoTime());
        stagingFailures.remove(session.code());
        stagingRemaining.put(session.code(), new AtomicInteger(participants.size()));
        for (int index = 0; index < participants.size(); index++) {
            ParticipantProgress participant = participants.get(index);
            stagingAssignments.put(participant.playerId(), track.slots().get(index));
            runForPlayer(participant.playerId(), player -> stageParticipant(session.code(), participant.playerId(), player));
        }
        broadcast(session, "room-starting", Map.of(), null);
    }

    private void stageParticipant(String code, UUID playerId, Player player) {
        RaceSession session = sessions.get(code);
        Track track = session == null ? null : tracks.get(session.trackId()).orElse(null);
        StartSlot slot = stagingAssignments.get(playerId);
        if (session == null || track == null || slot == null || session.phase() != RacePhase.STAGING) {
            failStaging(code, "赛道或参赛者状态已变化");
            return;
        }
        World world = Bukkit.getWorld(track.worldId());
        if (world == null) {
            failStaging(code, "赛道世界未加载");
            return;
        }
        stagingOrigins.putIfAbsent(playerId, StoredLocation.from(player.getLocation()));
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Location destination = new Location(world, slot.x(), slot.y(), slot.z(), slot.yaw(), slot.pitch());
        scheduler.teleport(player, destination).whenComplete((success, failure) -> {
            if (failure == null && Boolean.TRUE.equals(success)) {
                stagedPlayers.add(playerId);
            }
            runForPlayer(playerId, owned -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                failStaging(code, "玩家传送失败");
                return;
            }
            RaceSession current = sessions.get(code);
            if (current == null || current.phase() != RacePhase.STAGING) {
                return;
            }
            World targetWorld = Bukkit.getWorld(track.worldId());
            if (targetWorld == null) {
                failStaging(code, "赛道世界未加载");
                return;
            }
            Location stagedLocation = new Location(targetWorld, slot.x(), slot.y(), slot.z(), slot.yaw(), slot.pitch());
            Entity spawned = targetWorld.spawnEntity(stagedLocation, settings.get().raceBoatType());
            if (!(spawned instanceof Boat boat)) {
                spawned.remove();
                failStaging(code, "配置的实体不是船");
                return;
            }
            boat.setInvulnerable(true);
            boat.setPersistent(false);
            boat.getPersistentDataContainer().set(boatKey, PersistentDataType.BYTE, (byte) 1);
            raceBoats.add(boat.getUniqueId());
            boatOwners.put(boat.getUniqueId(), playerId);
            database.registerBoat(boat.getUniqueId()).exceptionally(databaseFailure -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to register BoatRace boat", databaseFailure);
                return null;
            });
            if (!boat.addPassenger(owned)) {
                removeBoatNow(boat);
                failStaging(code, "玩家无法进入比赛船");
                return;
            }
            if (!current.markStaged(playerId, boat.getUniqueId())) {
                removeBoatNow(boat);
                failStaging(code, "参赛者准备状态无效");
                return;
            }
            AtomicInteger remaining = stagingRemaining.get(code);
            if (remaining != null && remaining.decrementAndGet() == 0) {
                beginCountdown(code);
            }
            });
        });
    }

    private void beginCountdown(String code) {
        RaceSession session = sessions.get(code);
        if (session == null || !session.beginCountdown()) {
            failStaging(code, "无法进入倒计时");
            return;
        }
        countdown(code, settings.get().countdownSeconds());
    }

    private void countdown(String code, int seconds) {
        RaceSession session = sessions.get(code);
        if (session == null || session.phase() != RacePhase.COUNTDOWN) {
            return;
        }
        if (seconds <= 0) {
            releaseRace(session);
            return;
        }
        for (ParticipantProgress participant : session.participants()) {
            runForPlayer(participant.playerId(), player -> player.showTitle(Title.title(
                messages.unprefixed("countdown-title", Map.of("seconds", String.valueOf(seconds))),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(850), Duration.ofMillis(150))
            )));
        }
        scheduler.runGlobalDelayed(() -> countdown(code, seconds - 1), 20L);
    }

    private void releaseRace(RaceSession session) {
        long nowNanos = System.nanoTime();
        long nowEpoch = System.currentTimeMillis();
        if (!session.beginRunning(nowNanos, nowEpoch)) {
            return;
        }
        for (ParticipantProgress participant : session.participants()) {
            runForPlayer(participant.playerId(), player -> {
                stagedPlayers.remove(participant.playerId());
                ParticipantProgress current = session.progress(participant.playerId()).orElse(null);
                if (current == null || current.boatId() == null) {
                    markDnf(session, participant.playerId(), false);
                    return;
                }
                Entity entity = player.getVehicle();
                if (!(entity instanceof Boat boat) || !boat.getUniqueId().equals(current.boatId()) || !Bukkit.isOwnedByCurrentRegion(boat)) {
                    markDnf(session, participant.playerId(), true);
                    return;
                }
                player.showTitle(Title.title(
                    messages.unprefixed("go-title"),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(250))
                ));
                startUi(player);
            });
        }
    }

    private void failStaging(String code, String reason) {
        if (!stagingFailures.add(code)) {
            return;
        }
        RaceSession session = sessions.get(code);
        if (session == null) {
            return;
        }
        List<ParticipantProgress> participants = session.participants();
        session.rollbackStaging(System.nanoTime());
        stagingRemaining.remove(code);
        for (ParticipantProgress participant : participants) {
            stagedPlayers.remove(participant.playerId());
            stagingAssignments.remove(participant.playerId());
            if (participant.boatId() != null) {
                removeBoat(participant.boatId());
            }
            StoredLocation origin = stagingOrigins.remove(participant.playerId());
            runForPlayer(participant.playerId(), player -> {
                if (origin != null) {
                    origin.resolve().ifPresent(location -> scheduler.teleport(player, location));
                }
                messages.send(player, "room-staging-failed", Map.of("reason", reason));
            });
        }
        stagingFailures.remove(code);
    }

    public void cancelByPlayer(Player player) {
        String code = sessionByPlayer.get(player.getUniqueId());
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        if (!session.ownerId().equals(player.getUniqueId()) && !player.hasPermission("boatrace.admin")) {
            messages.send(player, "room-owner-only");
            return;
        }
        if (session.phase() != RacePhase.WAITING) {
            messages.send(player, "room-not-waiting");
            return;
        }
        cancelSession(session, "room-cancelled");
    }

    public void cancelByAdmin(CommandSender sender, String rawCode) {
        String code = rawCode.toUpperCase();
        RaceSession session = sessions.get(code);
        if (session == null) {
            messages.send(sender, "room-not-found", Map.of("code", code));
            return;
        }
        cancelSession(session, "room-cancelled");
        messages.send(sender, "room-cancelled", Map.of("code", code));
    }

    public boolean pause(String rawCode) {
        RaceSession session = sessions.get(rawCode.toUpperCase());
        return session != null && session.pause(System.nanoTime());
    }

    public boolean resume(String rawCode) {
        RaceSession session = sessions.get(rawCode.toUpperCase());
        return session != null && session.resume(System.nanoTime());
    }

    public boolean end(String rawCode) {
        RaceSession session = sessions.get(rawCode.toUpperCase());
        if (session == null || session.phase() == RacePhase.WAITING || session.phase() == RacePhase.FINISHED || session.phase() == RacePhase.CANCELLED) {
            return false;
        }
        for (ParticipantProgress participant : session.participants()) {
            if (!participant.terminal()) {
                markDnf(session, participant.playerId(), true);
            }
        }
        finishIfComplete(session);
        return true;
    }

    public RaceSession sessionFor(UUID playerId) {
        String code = sessionByPlayer.get(playerId);
        return code == null ? null : sessions.get(code);
    }

    private void cancelSession(RaceSession session, String messageKey) {
        session.cancelPhase();
        List<ParticipantProgress> participants = session.participants();
        removeSessionMappings(session);
        for (ParticipantProgress participant : participants) {
            stagedPlayers.remove(participant.playerId());
            stagingAssignments.remove(participant.playerId());
            stagingOrigins.remove(participant.playerId());
            stopUi(participant.playerId());
            if (participant.boatId() != null) {
                removeBoat(participant.boatId());
            }
            send(participant.playerId(), messageKey, Map.of("code", session.code()));
        }
    }

    public void status(Player player) {
        String code = sessionByPlayer.get(player.getUniqueId());
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        session.touch(System.nanoTime());
        messages.send(player, "room-status", Map.of(
            "code", session.code(),
            "track", session.trackId(),
            "state", session.phase().name(),
            "players", String.valueOf(session.size()),
            "capacity", String.valueOf(session.capacity())
        ));
    }

    public int stopTrials(String trackId) {
        List<UUID> removed = trials.entrySet().stream()
            .filter(entry -> entry.getValue().trackId().equals(trackId))
            .map(Map.Entry::getKey)
            .toList();
        for (UUID playerId : removed) {
            trials.remove(playerId);
            routePenalties.remove(playerId);
            exitMessageTimes.remove(playerId);
            stopUi(playerId);
            send(playerId, "trial-cancelled", Map.of());
        }
        return removed.size();
    }

    public void handleVehicleMove(Boat boat, Player driver, Location fromLocation, Location toLocation) {
        UUID playerId = driver.getUniqueId();
        Point3 from = point(fromLocation);
        Point3 to = point(toLocation);
        String code = sessionByPlayer.get(playerId);
        if (code != null) {
            RaceSession session = sessions.get(code);
            if (session != null && (session.phase() == RacePhase.STAGING || session.phase() == RacePhase.COUNTDOWN)) {
                holdStagedBoat(session, boat, playerId, to);
            } else if (session != null && session.phase() == RacePhase.RUNNING && !routePenalties.containsKey(playerId)) {
                handleRaceMove(session, boat, driver, from, to);
            }
            return;
        }
        TrialRun trial = trials.get(playerId);
        if (trial != null) {
            if (!routePenalties.containsKey(playerId)) {
                handleTrialMove(trial, boat, driver, from, to);
            }
            return;
        }
        renderIdleStart(driver, to);
        List<Track> crossed = tracks.crossedStarts(toLocation.getWorld().getUID(), from, to).stream()
            .filter(track -> !sessionByTrack.containsKey(track.id()))
            .toList();
        if (crossed.size() == 1) {
            Track track = crossed.getFirst();
            long now = System.nanoTime();
            TrialRun created = new TrialRun(playerId, track.id(), boat.getUniqueId(), 0, now, now + settings.get().trialTimeoutNanos());
            trials.put(playerId, created);
            messages.send(driver, "trial-started");
            startUi(driver);
        }
    }

    private void holdStagedBoat(RaceSession session, Boat boat, UUID playerId, Point3 currentPosition) {
        ParticipantProgress progress = session.progress(playerId).orElse(null);
        StartSlot slot = stagingAssignments.get(playerId);
        if (progress == null || progress.boatId() == null || !progress.boatId().equals(boat.getUniqueId()) || slot == null) {
            return;
        }
        boat.setVelocity(new Vector());
        if (currentPosition.distanceSquared(slot.point()) <= 0.01 || !stagedCorrections.add(boat.getUniqueId())) {
            return;
        }
        UUID boatId = boat.getUniqueId();
        String trackId = session.trackId();
        scheduler.runEntityDelayed(boat, () -> {
            Entity entity = Bukkit.getEntity(boatId);
            Track track = tracks.get(trackId).orElse(null);
            World world = track == null ? null : Bukkit.getWorld(track.worldId());
            if (!(entity instanceof Boat owned) || world == null) {
                stagedCorrections.remove(boatId);
                return;
            }
            owned.setVelocity(new Vector());
            Location destination = new Location(world, slot.x(), slot.y(), slot.z(), slot.yaw(), slot.pitch());
            scheduler.teleport(owned, destination)
                .whenComplete((success, failure) -> stagedCorrections.remove(boatId));
        }, () -> stagedCorrections.remove(boatId), 1L);
    }

    private void beginBacktrackPenalty(Player player, Boat boat, Track track, int restoreCheckpointIndex) {
        if (restoreCheckpointIndex < 0 || restoreCheckpointIndex >= track.checkpoints().size()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        RoutePenalty penalty = new RoutePenalty(playerId, boat.getUniqueId(), track.id(), restoreCheckpointIndex);
        if (routePenalties.putIfAbsent(playerId, penalty) == null) {
            penaltyCountdown(playerId, settings.get().backtrackCountdownSeconds());
        }
    }

    private void penaltyCountdown(UUID playerId, int seconds) {
        RoutePenalty penalty = routePenalties.get(playerId);
        if (penalty == null) {
            return;
        }
        if (seconds <= 0) {
            restorePenalty(penalty);
            return;
        }
        runForPlayer(playerId, player -> player.showTitle(Title.title(
            messages.unprefixed("backtrack-title", Map.of("seconds", String.valueOf(seconds))),
            messages.unprefixed("backtrack-subtitle"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))
        )));
        scheduler.runGlobalDelayed(() -> penaltyCountdown(playerId, seconds - 1), 20L);
    }

    private void restorePenalty(RoutePenalty penalty) {
        scheduler.runGlobal(() -> {
            Entity entity = Bukkit.getEntity(penalty.boatId());
            if (!(entity instanceof Boat boat)) {
                routePenalties.remove(penalty.playerId(), penalty);
                return;
            }
            scheduler.runEntity(boat, () -> {
                if (!penalty.equals(routePenalties.get(penalty.playerId()))) {
                    return;
                }
                Track track = tracks.get(penalty.trackId()).orElse(null);
                World world = track == null ? null : Bukkit.getWorld(track.worldId());
                if (track == null || world == null || penalty.restoreCheckpointIndex() >= track.checkpoints().size()) {
                    routePenalties.remove(penalty.playerId(), penalty);
                    return;
                }
                Entity ownedEntity = Bukkit.getEntity(penalty.boatId());
                if (!(ownedEntity instanceof Boat owned)) {
                    routePenalties.remove(penalty.playerId(), penalty);
                    return;
                }
                Cuboid gate = track.checkpoints().get(penalty.restoreCheckpointIndex());
                Point3 center = gate.center();
                owned.setVelocity(new Vector());
                Location destination = new Location(world, center.x(), gate.minY() + 0.1, center.z(), owned.getYaw(), 0.0f);
                scheduler.teleport(owned, destination)
                    .whenComplete((success, failure) -> scheduler.runGlobalDelayed(() -> {
                        if (routePenalties.remove(penalty.playerId(), penalty) && Boolean.TRUE.equals(success) && failure == null) {
                            send(penalty.playerId(), "backtrack-returned", Map.of());
                        }
                    }, 20L));
            }, () -> routePenalties.remove(penalty.playerId(), penalty));
        });
    }

    private void handleTrialMove(TrialRun trial, Boat boat, Player driver, Point3 from, Point3 to) {
        if (!trial.boatId().equals(boat.getUniqueId())) {
            trials.remove(trial.playerId(), trial);
            stopUi(trial.playerId());
            messages.send(driver, "trial-cancelled");
            return;
        }
        Track track = tracks.get(trial.trackId()).orElse(null);
        if (track == null || sessionByTrack.containsKey(track.id())) {
            trials.remove(trial.playerId(), trial);
            stopUi(trial.playerId());
            messages.send(driver, "trial-cancelled");
            return;
        }
        long now = System.nanoTime();
        if (trial.nextCheckpoint() < track.checkpoints().size()) {
            OptionalInt crossed = ForwardCheckpointDetector.highestCrossed(track, trial.nextCheckpoint(), from, to);
            if (crossed.isPresent()) {
                TrialRun advanced = trial.advanceTo(crossed.getAsInt() + 1, now + settings.get().trialTimeoutNanos());
                trials.replace(trial.playerId(), trial, advanced);
                messages.send(driver, "checkpoint", Map.of(
                    "current", String.valueOf(advanced.nextCheckpoint()),
                    "total", String.valueOf(track.checkpoints().size())
                ));
                return;
            }
            BacktrackDetector.detect(track, trial.nextCheckpoint(), from, to)
                .ifPresent(hit -> beginBacktrackPenalty(driver, boat, track, hit.restoreCheckpointIndex()));
            return;
        }
        if (!track.start().crossed(from, to)) {
            BacktrackDetector.detect(track, trial.nextCheckpoint(), from, to)
                .ifPresent(hit -> beginBacktrackPenalty(driver, boat, track, hit.restoreCheckpointIndex()));
            return;
        }
        long elapsed = Math.max(0L, now - trial.lapStartedNanos());
        trials.replace(trial.playerId(), trial, trial.nextLap(now, now + settings.get().trialTimeoutNanos()));
        messages.send(driver, "trial-lap", Map.of("time", TimeFormatter.formatNanos(elapsed)));
        database.recordTrial(track.id(), trial.playerId(), driver.getName(), elapsed, System.currentTimeMillis())
            .whenComplete((result, failure) -> handleTrialSaved(trial.playerId(), track.id(), elapsed, result, failure));
    }

    private void handleTrialSaved(UUID playerId, String trackId, long elapsed, TrialSaveResult result, Throwable failure) {
        if (failure != null) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save BoatRace trial", failure);
            send(playerId, "database-error", Map.of());
            return;
        }
        leaderboards.update(trackId, result.topSeven());
        if (result.personalBest()) {
            send(playerId, "trial-personal-best", Map.of("time", TimeFormatter.formatNanos(elapsed)));
        }
    }

    private void handleRaceMove(RaceSession session, Boat boat, Player driver, Point3 from, Point3 to) {
        ParticipantProgress progress = session.progress(driver.getUniqueId()).orElse(null);
        if (progress == null || progress.status() != ParticipantStatus.RUNNING || !boat.getUniqueId().equals(progress.boatId())) {
            return;
        }
        Track track = tracks.get(session.trackId()).orElse(null);
        if (track == null) {
            markDnf(session, driver.getUniqueId(), true);
            finishIfComplete(session);
            return;
        }
        session.updatePosition(driver.getUniqueId(), to);
        if (progress.nextCheckpoint() < track.checkpoints().size()) {
            OptionalInt crossed = ForwardCheckpointDetector.highestCrossed(track, progress.nextCheckpoint(), from, to);
            if (crossed.isPresent()) {
                int recorded = crossed.getAsInt() + 1;
                session.advanceTo(driver.getUniqueId(), recorded, to);
                messages.send(driver, "checkpoint", Map.of(
                    "current", String.valueOf(recorded),
                    "total", String.valueOf(track.checkpoints().size())
                ));
                return;
            }
            BacktrackDetector.detect(track, progress.nextCheckpoint(), from, to)
                .ifPresent(hit -> beginBacktrackPenalty(driver, boat, track, hit.restoreCheckpointIndex()));
            return;
        }
        if (!track.start().crossed(from, to)) {
            BacktrackDetector.detect(track, progress.nextCheckpoint(), from, to)
                .ifPresent(hit -> beginBacktrackPenalty(driver, boat, track, hit.restoreCheckpointIndex()));
            return;
        }
        Optional<ParticipantProgress> finished = session.finish(driver.getUniqueId(), System.nanoTime(), to);
        if (finished.isEmpty()) {
            return;
        }
        removeBoatNow(boat);
        stopUi(driver.getUniqueId());
        if (finished.get().finishRank() == 1 && session.claimFirstFinisher()) {
            runForPlayer(driver.getUniqueId(), player -> {
                Firework firework = player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.setPower(1);
                meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(142, 197, 252)).withFade(Color.fromRGB(224, 195, 252)).trail(true).flicker(true).build());
                firework.setFireworkMeta(meta);
                messages.send(player, "gui-first-finish");
            });
        }
        messages.send(driver, "race-finished", Map.of(
            "rank", String.valueOf(finished.get().finishRank()),
            "time", TimeFormatter.formatNanos(finished.get().finishNanos())
        ));
        finishIfComplete(session);
    }

    private void renderIdleStart(Player player, Point3 position) {
        long now = System.nanoTime();
        long previous = idleParticleTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 250_000_000L) {
            return;
        }
        idleParticleTimes.put(player.getUniqueId(), now);
        tracks.nearestStart(player.getWorld().getUID(), position, settings.get().particleViewDistance())
            .filter(track -> !sessionByTrack.containsKey(track.id()))
            .ifPresent(track -> particles.gate(player, track.start(), Color.LIME));
    }

    public void handleQuit(UUID playerId) {
        idleParticleTimes.remove(playerId);
        routePenalties.remove(playerId);
        exitMessageTimes.remove(playerId);
        TrialRun trial = trials.remove(playerId);
        if (trial != null) {
            stopUi(playerId);
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            return;
        }
        RacePhase phase = session.phase();
        if (phase == RacePhase.WAITING) {
            session.removeWaiting(playerId, System.nanoTime());
            sessionByPlayer.remove(playerId, code);
        } else if (phase == RacePhase.STAGING || phase == RacePhase.COUNTDOWN) {
            failStaging(code, "有玩家在发车前离线");
            session.removeWaiting(playerId, System.nanoTime());
            sessionByPlayer.remove(playerId, code);
        } else if (phase == RacePhase.RUNNING) {
            markDnf(session, playerId, true);
            finishIfComplete(session);
        }
    }

    public void handleJoin(UUID playerId) {
        long now = System.nanoTime();
        sessions.values().stream()
            .filter(session -> session.phase() == RacePhase.WAITING && session.ownerId().equals(playerId))
            .forEach(session -> session.touch(now));
    }

    public void handleDeath(UUID playerId) {
        routePenalties.remove(playerId);
        exitMessageTimes.remove(playerId);
        TrialRun trial = trials.remove(playerId);
        if (trial != null) {
            stopUi(playerId);
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session != null && session.phase() == RacePhase.RUNNING) {
            markDnf(session, playerId, true);
            finishIfComplete(session);
        }
    }

    public void handleVehicleExit(Player player, Entity vehicle) {
        UUID playerId = player.getUniqueId();
        TrialRun trial = trials.get(playerId);
        if (trial != null && trial.boatId().equals(vehicle.getUniqueId())) {
            trials.remove(playerId, trial);
            stopUi(playerId);
            messages.send(player, "trial-cancelled");
            return;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session != null && session.phase() == RacePhase.RUNNING) {
            ParticipantProgress progress = session.progress(playerId).orElse(null);
            if (progress != null && progress.status() == ParticipantStatus.RUNNING && vehicle.getUniqueId().equals(progress.boatId())) {
                markDnf(session, playerId, true);
                finishIfComplete(session);
            }
        }
    }

    public boolean shouldBlockVehicleExit(Player player, Entity vehicle) {
        UUID playerId = player.getUniqueId();
        RoutePenalty penalty = routePenalties.get(playerId);
        if (penalty != null && penalty.boatId().equals(vehicle.getUniqueId())) {
            return true;
        }
        TrialRun trial = trials.get(playerId);
        if (trial != null && trial.boatId().equals(vehicle.getUniqueId())) {
            return true;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        ParticipantProgress progress = session == null ? null : session.progress(playerId).orElse(null);
        if (session == null || progress == null || progress.boatId() == null || !progress.boatId().equals(vehicle.getUniqueId())) {
            return false;
        }
        RacePhase phase = session.phase();
        return phase == RacePhase.STAGING || phase == RacePhase.COUNTDOWN
            || phase == RacePhase.RUNNING && progress.status() == ParticipantStatus.RUNNING;
    }

    public void notifyExitBlocked(Player player) {
        long now = System.nanoTime();
        long previous = exitMessageTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous >= 1_000_000_000L) {
            exitMessageTimes.put(player.getUniqueId(), now);
            messages.send(player, "exit-command-only");
        }
    }

    public boolean shouldCancelRaceBoatCollision(UUID boatId, Entity other) {
        if (!raceBoats.contains(boatId)) {
            return false;
        }
        return raceBoats.contains(other.getUniqueId())
            || other instanceof Player player && isProtectedPlayer(player.getUniqueId());
    }

    public boolean isProtectedPlayer(UUID playerId) {
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            return false;
        }
        RacePhase phase = session.phase();
        if (phase == RacePhase.STAGING || phase == RacePhase.COUNTDOWN) {
            return true;
        }
        ParticipantProgress progress = session.progress(playerId).orElse(null);
        return phase == RacePhase.RUNNING && progress != null && progress.status() == ParticipantStatus.RUNNING;
    }

    public boolean isRaceBoat(UUID entityId) {
        return raceBoats.contains(entityId);
    }

    public boolean isStaged(UUID playerId) {
        return stagedPlayers.contains(playerId);
    }

    private void markDnf(RaceSession session, UUID playerId, boolean removeBoat) {
        routePenalties.remove(playerId);
        exitMessageTimes.remove(playerId);
        ParticipantProgress progress = session.dnf(playerId).orElse(null);
        stagedPlayers.remove(playerId);
        stopUi(playerId);
        if (progress != null && removeBoat && progress.boatId() != null) {
            removeBoat(progress.boatId());
        }
        send(playerId, "race-dnf", Map.of());
    }

    private void finishIfComplete(RaceSession session) {
        if (session.allTerminal()) {
            finishSession(session);
        }
    }

    private void finishSession(RaceSession session) {
        if (!endingSessions.add(session.code())) {
            return;
        }
        session.finishPhase();
        long endedAt = System.currentTimeMillis();
        LastRace race = new LastRace(
            session.trackId(),
            session.code(),
            session.goEpochMillis() == 0L ? session.createdEpochMillis() : session.goEpochMillis(),
            endedAt,
            session.resultEntries()
        );
        lastRaces.update(race);
        RaceResultEntry winner = race.entries().stream().filter(RaceResultEntry::finished).findFirst().orElse(null);
        if (winner == null) {
            scheduler.runGlobal(() -> Bukkit.broadcast(messages.component("gui-race-no-winner-broadcast")));
        } else {
            scheduler.runGlobal(() -> Bukkit.broadcast(messages.component("gui-race-winner-broadcast", Map.of(
                "player", winner.playerName(), "time", TimeFormatter.formatNanos(winner.elapsedNanos())))));
        }
        database.saveLastRace(race).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to save BoatRace result", failure);
            return null;
        });
        List<ParticipantProgress> participants = session.participants();
        removeSessionMappings(session);
        for (ParticipantProgress participant : participants) {
            stopUi(participant.playerId());
            if (participant.boatId() != null) {
                removeBoat(participant.boatId());
            }
            sendResults(participant.playerId(), race);
        }
        endingSessions.remove(session.code());
    }

    private void sendResults(UUID playerId, LastRace race) {
        runForPlayer(playerId, player -> {
            messages.send(player, "race-results-header", Map.of("code", race.code()));
            for (RaceResultEntry entry : race.entries()) {
                if (entry.finished()) {
                    messages.send(player, "race-results-entry", Map.of(
                        "rank", String.valueOf(entry.rank()),
                        "player", entry.playerName(),
                        "time", TimeFormatter.formatNanos(entry.elapsedNanos())
                    ));
                } else {
                    messages.send(player, "race-results-dnf", Map.of("player", entry.playerName()));
                }
            }
        });
    }

    private void removeSessionMappings(RaceSession session) {
        sessions.remove(session.code(), session);
        sessionByTrack.remove(session.trackId(), session.code());
        for (ParticipantProgress participant : session.participants()) {
            sessionByPlayer.remove(participant.playerId(), session.code());
            routePenalties.remove(participant.playerId());
            exitMessageTimes.remove(participant.playerId());
            stagingOrigins.remove(participant.playerId());
            stagingAssignments.remove(participant.playerId());
            stagedPlayers.remove(participant.playerId());
        }
        stagingRemaining.remove(session.code());
        stagingFailures.remove(session.code());
    }

    private void cleanupTick() {
        long now = System.nanoTime();
        for (Map.Entry<UUID, TrialRun> entry : trials.entrySet()) {
            if (now >= entry.getValue().expiresAtNanos() && trials.remove(entry.getKey(), entry.getValue())) {
                routePenalties.remove(entry.getKey());
                exitMessageTimes.remove(entry.getKey());
                stopUi(entry.getKey());
                send(entry.getKey(), "trial-timeout", Map.of());
            }
        }
        for (RaceSession session : List.copyOf(sessions.values())) {
            if (session.phase() == RacePhase.WAITING && now - session.lastActivityNanos() >= settings.get().lobbyIdleTimeoutNanos()) {
                cancelSession(session, "room-expired");
            } else if (session.phase() == RacePhase.RUNNING && now - session.goNanos() >= settings.get().raceTimeoutNanos()) {
                for (ParticipantProgress participant : session.participants()) {
                    if (!participant.terminal()) {
                        markDnf(session, participant.playerId(), true);
                    }
                }
                finishIfComplete(session);
            }
        }
    }

    private void startUi(Player player) {
        UUID playerId = player.getUniqueId();
        TaskHandle task = scheduler.runEntityRepeating(
            player,
            () -> renderUi(playerId),
            () -> stopUi(playerId),
            1L,
            settings.get().particlePeriodTicks()
        );
        TaskHandle previous = uiTasks.put(playerId, task);
        if (previous != null) {
            previous.cancel();
        }
    }

    private void renderUi(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            stopUi(playerId);
            return;
        }
        TrialRun trial = trials.get(playerId);
        if (trial != null) {
            Track track = tracks.get(trial.trackId()).orElse(null);
            if (track == null || !track.worldId().equals(player.getWorld().getUID())) {
                return;
            }
            Cuboid target = track.target(trial.nextCheckpoint());
            particles.gate(player, target, trial.nextCheckpoint() < track.checkpoints().size() ? Color.AQUA : Color.LIME);
            long elapsed = Math.max(0L, System.nanoTime() - trial.lapStartedNanos());
            player.sendActionBar(Component.text("自由计时 " + TimeFormatter.formatNanos(elapsed) + " | 记录点 " + Math.min(trial.nextCheckpoint() + 1, track.checkpoints().size()) + "/" + track.checkpoints().size(), NamedTextColor.AQUA));
            return;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null || session.phase() != RacePhase.RUNNING) {
            stopUi(playerId);
            return;
        }
        ParticipantProgress progress = session.progress(playerId).orElse(null);
        Track track = tracks.get(session.trackId()).orElse(null);
        if (progress == null || track == null || progress.status() != ParticipantStatus.RUNNING || !track.worldId().equals(player.getWorld().getUID())) {
            return;
        }
        String leader = session.leaderName();
        if (leader == null) {
            player.sendActionBar(messages.unprefixed("gui-leaderboard-empty-actionbar"));
        } else {
            player.sendActionBar(messages.unprefixed("gui-leaderboard-actionbar", Map.of(
                "player", leader, "time", TimeFormatter.formatNanos(session.leaderTimeNanos()))));
        }
        Cuboid target = track.target(progress.nextCheckpoint());
        particles.gate(player, target, progress.nextCheckpoint() < track.checkpoints().size() ? Color.AQUA : Color.LIME);
        int rank = liveRank(session, track, playerId);
        long elapsed = Math.max(0L, System.nanoTime() - session.goNanos());
        player.sendActionBar(Component.text("第 " + rank + "/" + session.size() + " 名 | " + TimeFormatter.formatNanos(elapsed) + " | 记录点 " + Math.min(progress.nextCheckpoint() + 1, track.checkpoints().size()) + "/" + track.checkpoints().size(), NamedTextColor.AQUA));
    }

    private int liveRank(RaceSession session, Track track, UUID playerId) {
        List<ParticipantProgress> values = new ArrayList<>(session.participants());
        values.sort(Comparator
            .comparingInt((ParticipantProgress value) -> statusOrder(value.status()))
            .thenComparingInt(value -> value.status() == ParticipantStatus.FINISHED ? value.finishRank() : -value.nextCheckpoint())
            .thenComparingDouble(value -> distanceToTarget(value, track))
            .thenComparingInt(ParticipantProgress::joinOrder));
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).playerId().equals(playerId)) {
                return index + 1;
            }
        }
        return values.size();
    }

    private static int statusOrder(ParticipantStatus status) {
        return switch (status) {
            case FINISHED -> 0;
            case RUNNING -> 1;
            case STAGED, WAITING -> 2;
            case DNF -> 3;
        };
    }

    private static double distanceToTarget(ParticipantProgress progress, Track track) {
        if (progress.position() == null || progress.status() != ParticipantStatus.RUNNING) {
            return Double.MAX_VALUE;
        }
        return progress.position().distanceSquared(track.target(progress.nextCheckpoint()).center());
    }

    private void stopUi(UUID playerId) {
        TaskHandle task = uiTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void removeBoatNow(Boat boat) {
        UUID boatId = boat.getUniqueId();
        raceBoats.remove(boatId);
        stagedCorrections.remove(boatId);
        routePenalties.entrySet().removeIf(entry -> entry.getValue().boatId().equals(boatId));
        boatOwners.remove(boatId);
        database.unregisterBoat(boatId).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to unregister BoatRace boat", failure);
            return null;
        });
        boat.eject();
        boat.remove();
    }

    private void removeBoat(UUID boatId) {
        raceBoats.remove(boatId);
        stagedCorrections.remove(boatId);
        routePenalties.entrySet().removeIf(entry -> entry.getValue().boatId().equals(boatId));
        boatOwners.remove(boatId);
        database.unregisterBoat(boatId).exceptionally(failure -> null);
        scheduler.runGlobal(() -> {
            Entity entity = Bukkit.getEntity(boatId);
            if (entity == null) {
                return;
            }
            scheduler.runEntity(entity, () -> {
                Entity owned = Bukkit.getEntity(boatId);
                if (owned != null) {
                    owned.eject();
                    owned.remove();
                }
            }, null);
        });
    }

    private void broadcast(RaceSession session, String key, Map<String, String> values, UUID excluded) {
        for (ParticipantProgress participant : session.participants()) {
            if (excluded == null || !excluded.equals(participant.playerId())) {
                send(participant.playerId(), key, values);
            }
        }
    }

    private void send(UUID playerId, String key, Map<String, String> values) {
        runForPlayer(playerId, player -> messages.send(player, key, values));
    }

    private void runForPlayer(UUID playerId, Consumer<Player> action) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return;
            }
            scheduler.runEntity(player, () -> {
                Player owned = Bukkit.getPlayer(playerId);
                if (owned != null) {
                    action.accept(owned);
                }
            }, null);
        });
    }

    private static Point3 point(Location location) {
        return new Point3(location.getX(), location.getY(), location.getZ());
    }

    public Map<String, RaceSession> sessionSnapshot() {
        return Map.copyOf(sessions);
    }

    public void shutdown() {
        cleanupTask.cancel();
        uiTasks.values().forEach(TaskHandle::cancel);
        uiTasks.clear();
        raceBoats.clear();
        boatOwners.clear();
        sessions.clear();
        sessionByTrack.clear();
        sessionByPlayer.clear();
        trials.clear();
        stagingOrigins.clear();
        stagingAssignments.clear();
        stagingRemaining.clear();
        stagingFailures.clear();
        stagedPlayers.clear();
        stagedCorrections.clear();
        routePenalties.clear();
        exitMessageTimes.clear();
        idleParticleTimes.clear();
        endingSessions.clear();
    }
}
