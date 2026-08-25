package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.config.PluginSettings;
import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.EditorSession;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.PointSelection;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.model.TrackDraft;
import cn.cloudfl4re.boatrace.persistence.DatabaseService;
import cn.cloudfl4re.boatrace.scheduler.SchedulerFacade;
import cn.cloudfl4re.boatrace.scheduler.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class EditorManager {
    private final TrackService tracks;
    private final DatabaseService database;
    private final SchedulerFacade scheduler;
    private final MessageService messages;
    private final Supplier<PluginSettings> settings;
    private final ParticleRenderer particles;
    private final ConcurrentHashMap<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TaskHandle> previewTasks = new ConcurrentHashMap<>();

    public EditorManager(TrackService tracks, DatabaseService database, SchedulerFacade scheduler, MessageService messages, Supplier<PluginSettings> settings, ParticleRenderer particles) {
        this.tracks = tracks;
        this.database = database;
        this.scheduler = scheduler;
        this.messages = messages;
        this.settings = settings;
        this.particles = particles;
    }

    public boolean beginCreate(Player player, String id, String displayName) {
        if (tracks.get(id).isPresent() || locks.putIfAbsent(id, player.getUniqueId()) != null) {
            return false;
        }
        replaceSession(player, EditorSession.create(TrackDraft.empty(id, displayName)));
        return true;
    }

    public boolean beginEdit(Player player, Track track) {
        UUID current = locks.putIfAbsent(track.id(), player.getUniqueId());
        if (current != null && !current.equals(player.getUniqueId())) {
            return false;
        }
        replaceSession(player, EditorSession.create(TrackDraft.from(track)));
        return true;
    }

    private void replaceSession(Player player, EditorSession session) {
        cancel(player.getUniqueId());
        locks.put(session.draft().id(), player.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        startPreview(player);
    }

    public Optional<EditorSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public boolean setPosition(Player player, boolean first) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation();
        Point3 point = new Point3(location.getX(), location.getY(), location.getZ());
        return sessions.computeIfPresent(playerId, (ignored, current) -> {
            PointSelection selection = first
                ? current.selection().withFirst(location.getWorld().getUID(), point)
                : current.selection().withSecond(location.getWorld().getUID(), point);
            return current.withSelection(selection);
        }) != null;
    }

    public boolean setStart(UUID playerId) {
        final boolean[] changed = {false};
        sessions.computeIfPresent(playerId, (ignored, current) -> {
            if (!current.selection().complete()) {
                return current;
            }
            if (current.draft().worldId() != null
                && !current.draft().worldId().equals(current.selection().worldId())
                && (!current.draft().checkpoints().isEmpty() || !current.draft().slots().isEmpty())) {
                return current;
            }
            TrackDraft draft = current.draft().withStart(current.selection().worldId(), current.selection().cuboid());
            changed[0] = true;
            return current.withDraft(draft);
        });
        return changed[0];
    }

    public boolean addCheckpoint(UUID playerId) {
        return updateWithSelection(playerId, (draft, gate) -> draft.addCheckpoint(gate));
    }

    public boolean setCheckpoint(UUID playerId, int index) {
        return updateWithSelection(playerId, (draft, gate) -> draft.setCheckpoint(index, gate));
    }

    private boolean updateWithSelection(UUID playerId, GateUpdate update) {
        final boolean[] changed = {false};
        sessions.computeIfPresent(playerId, (ignored, current) -> {
            if (!current.selection().complete()) {
                return current;
            }
            if (current.draft().worldId() == null || !current.draft().worldId().equals(current.selection().worldId())) {
                return current;
            }
            try {
                TrackDraft draft = update.apply(current.draft(), current.selection().cuboid());
                changed[0] = true;
                return current.withDraft(draft);
            } catch (IndexOutOfBoundsException exception) {
                return current;
            }
        });
        return changed[0];
    }

    public boolean removeCheckpoint(UUID playerId, int index) {
        return updateDraft(playerId, draft -> draft.removeCheckpoint(index));
    }

    public boolean moveCheckpoint(UUID playerId, int from, int to) {
        return updateDraft(playerId, draft -> draft.moveCheckpoint(from, to));
    }

    public boolean addSlot(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation();
        final boolean[] changed = {false};
        sessions.computeIfPresent(playerId, (ignored, current) -> {
            if (current.draft().worldId() == null || !current.draft().worldId().equals(location.getWorld().getUID())) {
                return current;
            }
            StartSlot slot = new StartSlot(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            changed[0] = true;
            return current.withDraft(current.draft().addSlot(slot));
        });
        return changed[0];
    }

    public boolean removeSlot(UUID playerId, int index) {
        return updateDraft(playerId, draft -> draft.removeSlot(index));
    }

    private boolean updateDraft(UUID playerId, DraftUpdate update) {
        final boolean[] changed = {false};
        sessions.computeIfPresent(playerId, (ignored, current) -> {
            try {
                changed[0] = true;
                return current.withDraft(update.apply(current.draft()));
            } catch (IndexOutOfBoundsException exception) {
                return current;
            }
        });
        return changed[0];
    }

    public boolean setPreview(UUID playerId, boolean value) {
        return sessions.computeIfPresent(playerId, (ignored, current) -> current.withPreview(value)) != null;
    }

    public void save(Player player) {
        UUID playerId = player.getUniqueId();
        EditorSession session = sessions.get(playerId);
        if (session == null) {
            messages.send(player, "edit-no-session");
            return;
        }
        Optional<String> validation = TrackValidator.validate(session.draft(), tracks.all());
        if (validation.isPresent()) {
            messages.send(player, "edit-validation", Map.of("reason", validation.get()));
            return;
        }
        Track track = session.draft().toTrack();
        database.saveTrack(track).whenComplete((ignored, failure) -> {
            if (failure != null) {
                send(playerId, "database-error", Map.of());
                return;
            }
            tracks.put(track);
            sessions.remove(playerId);
            locks.remove(track.id(), playerId);
            TaskHandle task = previewTasks.remove(playerId);
            if (task != null) {
                task.cancel();
            }
            send(playerId, "track-saved", Map.of("track", track.id()));
        });
    }

    private void send(UUID playerId, String key, Map<String, String> values) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                scheduler.runEntity(player, () -> {
                    Player owned = Bukkit.getPlayer(playerId);
                    if (owned != null) {
                        messages.send(owned, key, values);
                    }
                }, null);
            }
        });
    }

    public boolean cancel(UUID playerId) {
        EditorSession removed = sessions.remove(playerId);
        if (removed == null) {
            return false;
        }
        locks.remove(removed.draft().id(), playerId);
        TaskHandle task = previewTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        return true;
    }

    public boolean isLocked(String trackId) {
        return locks.containsKey(trackId);
    }

    private void startPreview(Player player) {
        UUID playerId = player.getUniqueId();
        TaskHandle previous = previewTasks.put(playerId, scheduler.runEntityRepeating(
            player,
            () -> renderPreview(playerId),
            () -> cancel(playerId),
            1L,
            settings.get().particlePeriodTicks()
        ));
        if (previous != null) {
            previous.cancel();
        }
    }

    private void renderPreview(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        EditorSession session = sessions.get(playerId);
        if (player == null || session == null || !session.preview()) {
            return;
        }
        TrackDraft draft = session.draft();
        if (draft.worldId() != null && draft.worldId().equals(player.getWorld().getUID())) {
            if (draft.start() != null) {
                particles.gate(player, draft.start(), Color.LIME);
            }
            for (Cuboid checkpoint : draft.checkpoints()) {
                particles.gate(player, checkpoint, Color.AQUA);
            }
            for (StartSlot slot : draft.slots()) {
                particles.slot(player, slot);
            }
        }
        PointSelection selection = session.selection();
        if (selection.complete() && selection.worldId().equals(player.getWorld().getUID())) {
            particles.gate(player, selection.cuboid(), Color.WHITE);
        }
    }

    public void shutdown() {
        previewTasks.values().forEach(TaskHandle::cancel);
        previewTasks.clear();
        sessions.clear();
        locks.clear();
    }

    @FunctionalInterface
    private interface DraftUpdate {
        TrackDraft apply(TrackDraft draft);
    }

    @FunctionalInterface
    private interface GateUpdate {
        TrackDraft apply(TrackDraft draft, Cuboid gate);
    }
}
