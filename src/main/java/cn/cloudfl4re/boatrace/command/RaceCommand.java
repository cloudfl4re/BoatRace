package cn.cloudfl4re.boatrace.command;

import cn.cloudfl4re.boatrace.BoatRacePlugin;
import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.model.EditorSession;
import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.RaceResultEntry;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.service.EditorManager;
import cn.cloudfl4re.boatrace.service.LastRaceService;
import cn.cloudfl4re.boatrace.service.LeaderboardService;
import cn.cloudfl4re.boatrace.service.RaceManager;
import cn.cloudfl4re.boatrace.service.TrackService;
import cn.cloudfl4re.boatrace.service.TrackValidator;
import cn.cloudfl4re.boatrace.util.TimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

public final class RaceCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final BoatRacePlugin plugin;
    private final TrackService tracks;
    private final LeaderboardService leaderboards;
    private final LastRaceService lastRaces;
    private final EditorManager editors;
    private final RaceManager races;
    private final MessageService messages;

    public RaceCommand(BoatRacePlugin plugin) {
        this.plugin = plugin;
        this.tracks = plugin.tracks();
        this.leaderboards = plugin.leaderboards();
        this.lastRaces = plugin.lastRaces();
        this.editors = plugin.editors();
        this.races = plugin.races();
        this.messages = plugin.messages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String key : List.of("help-header", "help-gui", "help-create", "help-join", "help-laps", "help-start", "help-leave", "help-cancel", "help-status-name", "help-spec-name", "help-rank", "help-last", "help-trial")) {
                messages.send(sender, key);
            }
            if (sender.hasPermission("boatrace.admin")) {
                for (String key : List.of("help-admin-header", "help-track", "help-selection", "help-start-gate", "help-checkpoint", "help-slot", "help-save", "help-force", "help-trial-delete", "help-admin-penalty")) {
                    messages.send(sender, key);
                }
            }
            return true;
        }
        if (!plugin.isReady()) {
            messages.send(sender, "initializing");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui" -> gui(sender, args);
            case "create" -> create(sender);
            case "join" -> join(sender, args);
            case "laps" -> laps(sender, args);
            case "leave" -> leave(sender);
            case "start" -> start(sender);
            case "cancel" -> cancel(sender);
            case "status" -> status(sender);
            case "spec" -> spec(sender, args);
            case "rank" -> rank(sender);
            case "last" -> last(sender, args);
            case "trial" -> trial(sender, args);
            case "track" -> track(sender, args);
            case "edit" -> edit(sender, args);
            case "force" -> force(sender, args);
            case "admin" -> admin(sender, args);
            case "reload" -> reload(sender);
            default -> {
                messages.send(sender, "unknown-command");
                yield true;
            }
        };
    }

    private boolean gui(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!player.hasPermission("boatrace.gui")) {
            messages.send(player, "gui-no-permission");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("view")) {
            if (!player.hasPermission("boatrace.gui.view") && !player.hasPermission("boatrace.admin")) {
                messages.send(player, "gui-no-permission");
                return true;
            }
            String menu = args.length >= 3 ? args[2] : "main";
            if (plugin.guiConfigs() == null || plugin.guiConfigs().get(menu) == null) {
                messages.send(player, "gui-invalid-menu");
                return true;
            }
            plugin.gui().open(player, menu, true);
            messages.send(player, "gui-layout-opened");
            return true;
        }
        plugin.gui().open(player, "main");
        return true;
    }

    private boolean create(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            if (!player.hasPermission("boatrace.create")) {
                messages.send(player, "no-permission");
            } else {
                races.createRoom(player);
            }
        }
        return true;
    }

    private boolean join(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player != null) {
            if (args.length < 2) {
                messages.send(player, "unknown-command");
            } else {
                races.joinRoom(player, args[1]);
            }
        }
        return true;
    }

    private boolean laps(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "invalid-laps", Map.of("max", String.valueOf(RaceManager.MAX_FORMAL_LAPS)));
            return true;
        }
        Integer value = parseInteger(args[1]);
        if (value == null) {
            messages.send(player, "invalid-laps", Map.of("max", String.valueOf(RaceManager.MAX_FORMAL_LAPS)));
            return true;
        }
        races.configureLaps(player, value);
        return true;
    }

    private boolean leave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            races.leave(player);
        }
        return true;
    }

    private boolean start(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            races.startByPlayer(player);
        }
        return true;
    }

    private boolean cancel(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            races.cancelByPlayer(player);
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            races.status(player);
        }
        return true;
    }

    private boolean spec(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "spectator-usage-name");
            return true;
        }
        if (args[1].equalsIgnoreCase("join")) {
            if (args.length < 3) {
                messages.send(player, "spectator-usage-name");
            } else {
                races.joinSpectator(player, args[2]);
            }
        } else if (args[1].equalsIgnoreCase("leave")) {
            if (!races.leaveSpectator(player)) {
                messages.send(player, "spectator-not-active");
            }
        } else if (args[1].equalsIgnoreCase("tp")) {
            if (args.length < 3) {
                messages.send(player, "spectator-usage-name");
            } else {
                races.teleportSpectator(player, args[2]);
            }
        } else {
            messages.send(player, "spectator-usage-name");
        }
        return true;
    }

    private boolean rank(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            races.rank(player);
        }
        return true;
    }

    private boolean last(CommandSender sender, String[] args) {
        Track track = null;
        if (args.length >= 2) {
            track = tracks.get(args[1].toLowerCase(Locale.ROOT)).orElse(null);
        } else if (sender instanceof Player player) {
            Location location = player.getLocation();
            track = tracks.atStart(location.getWorld().getUID(), new Point3(location.getX(), location.getY(), location.getZ())).orElse(null);
        }
        if (track == null) {
            messages.send(sender, "track-not-found", Map.of("track", args.length >= 2 ? args[1] : "当前位置"));
            return true;
        }
        LastRace race = lastRaces.get(track.id()).orElse(null);
        if (race == null) {
            messages.send(sender, "last-race-empty");
            return true;
        }
        messages.send(sender, "last-race-header", Map.of("track", track.displayName()));
        for (RaceResultEntry entry : race.entries()) {
            if (entry.finished()) {
                messages.send(sender, "race-results-entry", Map.of(
                    "rank", String.valueOf(entry.rank()),
                    "player", entry.playerName(),
                    "time", TimeFormatter.formatNanos(entry.elapsedNanos())
                ));
            } else {
                messages.send(sender, "race-results-dnf", Map.of(
                    "player", entry.playerName(),
                    "laps", String.valueOf(entry.completedLaps()),
                    "total", String.valueOf(entry.totalLaps()),
                    "time", TimeFormatter.formatNanos(entry.elapsedNanos())
                ));
            }
        }
        return true;
    }

    private boolean trial(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("delete")) {
            messages.send(sender, "trial-record-delete-usage");
            return true;
        }
        return deleteTrialRecord(sender, args, 2);
    }

    private boolean deleteTrialRecord(CommandSender sender, String[] args, int trackIndex) {
        int playerIndex = trackIndex + 1;
        if (args.length <= playerIndex || !"confirm".equalsIgnoreCase(args.length > playerIndex + 1 ? args[playerIndex + 1] : "")) {
            messages.send(sender, "trial-record-delete-usage");
            return true;
        }
        String trackId = args[trackIndex].toLowerCase(Locale.ROOT);
        String playerReference = args[playerIndex].trim();
        if (tracks.get(trackId).isEmpty()) {
            messages.send(sender, "track-not-found", Map.of("track", trackId));
            return true;
        }
        UUID replyPlayer = sender instanceof Player player ? player.getUniqueId() : null;
        boolean replyConsole = sender instanceof ConsoleCommandSender;
        plugin.database().deleteTrialRecord(trackId, playerReference).whenComplete((result, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to delete BoatRace trial record", failure);
                reply(replyPlayer, replyConsole, "database-error", Map.of());
                return;
            }
            leaderboards.update(trackId, result.topRecords(), result.recordCount());
            plugin.personalStats().invalidate(trackId);
            String messageKey = result.removed() ? "trial-record-deleted" : "trial-record-not-found";
            reply(replyPlayer, replyConsole, messageKey, Map.of(
                "track", trackId,
                "player", playerReference,
                "count", String.valueOf(result.deletedCount())
            ));
        });
        return true;
    }

    private boolean track(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "unknown-command");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> trackCreate(sender, args);
            case "edit" -> trackEdit(sender, args);
            case "list" -> trackList(sender);
            case "info" -> trackInfo(sender, args);
            case "delete" -> trackDelete(sender, args);
            default -> {
                messages.send(sender, "unknown-command");
                yield true;
            }
        };
    }

    private boolean trackCreate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 4) {
            messages.send(player, "unknown-command");
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!TrackValidator.validId(id)) {
            messages.send(player, "track-id-invalid");
            return true;
        }
        if (tracks.get(id).isPresent()) {
            messages.send(player, "track-exists", Map.of("track", id));
            return true;
        }
        String displayName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!editors.beginCreate(player, id, displayName)) {
            messages.send(player, "track-locked");
        } else {
            messages.send(player, "track-created", Map.of("track", id));
        }
        return true;
    }

    private boolean trackEdit(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 3) {
            messages.send(player, "unknown-command");
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        Track track = tracks.get(id).orElse(null);
        if (track == null) {
            messages.send(player, "track-not-found", Map.of("track", id));
        } else if (races.isTrackInUse(id)) {
            messages.send(player, "track-in-use");
        } else if (!editors.beginEdit(player, track)) {
            messages.send(player, "track-locked");
        } else {
            messages.send(player, "track-editing", Map.of("track", id));
        }
        return true;
    }

    private boolean trackList(CommandSender sender) {
        if (tracks.all().isEmpty()) {
            messages.send(sender, "track-list-empty");
            return true;
        }
        messages.send(sender, "track-list-header");
        tracks.all().stream().sorted(java.util.Comparator.comparing(Track::id)).forEach(track -> messages.send(sender, "track-list-entry", Map.of("track", track.id(), "name", track.displayName())));
        return true;
    }

    private boolean trackInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "unknown-command");
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        Track track = tracks.get(id).orElse(null);
        if (track == null) {
            messages.send(sender, "track-not-found", Map.of("track", id));
        } else {
            messages.send(sender, "track-info", Map.of(
                "name", track.displayName(),
                "track", track.id(),
                "checkpoints", String.valueOf(track.checkpoints().size()),
                "slots", String.valueOf(track.slots().size())
            ));
        }
        return true;
    }

    private boolean trackDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "unknown-command");
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (tracks.get(id).isEmpty()) {
            messages.send(sender, "track-not-found", Map.of("track", id));
            return true;
        }
        if (races.isTrackInUse(id) || editors.isLocked(id)) {
            messages.send(sender, "track-in-use");
            return true;
        }
        if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
            messages.send(sender, "track-delete-confirm", Map.of("track", id));
            return true;
        }
        UUID replyPlayer = sender instanceof Player player ? player.getUniqueId() : null;
        boolean replyConsole = sender instanceof ConsoleCommandSender;
        plugin.database().deleteTrack(id).whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to delete BoatRace track", failure);
                reply(replyPlayer, replyConsole, "database-error", Map.of());
                return;
            }
            tracks.remove(id);
            leaderboards.remove(id);
            plugin.personalStats().invalidate(id);
            lastRaces.remove(id);
            reply(replyPlayer, replyConsole, "track-deleted", Map.of("track", id));
        });
        return true;
    }

    private boolean edit(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        EditorSession session = editors.session(player.getUniqueId()).orElse(null);
        if (session == null) {
            messages.send(player, "edit-no-session");
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "unknown-command");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "pos1" -> editPosition(player, true);
            case "pos2" -> editPosition(player, false);
            case "start" -> editStart(player);
            case "checkpoint" -> editCheckpoint(player, args);
            case "slot" -> editSlot(player, args);
            case "preview" -> editPreview(player, args);
            case "save" -> editSave(player);
            case "cancel" -> editCancel(player);
            default -> {
                messages.send(player, "unknown-command");
                yield true;
            }
        };
    }

    private boolean editPosition(Player player, boolean first) {
        editors.setPosition(player, first);
        messages.send(player, first ? "selection-pos1" : "selection-pos2");
        return true;
    }

    private boolean editStart(Player player) {
        EditorSession session = editors.session(player.getUniqueId()).orElse(null);
        if (session == null || !session.selection().complete() || !editors.setStart(player.getUniqueId())) {
            messages.send(player, "selection-missing");
        } else {
            messages.send(player, "edit-start-set");
        }
        return true;
    }

    private boolean editCheckpoint(Player player, String[] args) {
        if (args.length < 3) {
            messages.send(player, "unknown-command");
            return true;
        }
        UUID playerId = player.getUniqueId();
        EditorSession session = editors.session(playerId).orElseThrow();
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("add")) {
            if (editors.addCheckpoint(playerId)) {
                int value = editors.session(playerId).orElseThrow().draft().checkpoints().size();
                messages.send(player, "edit-checkpoint-added", Map.of("index", String.valueOf(value)));
            } else {
                messages.send(player, "selection-missing");
            }
            return true;
        }
        if (action.equals("set") || action.equals("remove")) {
            Integer value = args.length >= 4 ? index(args[3]) : null;
            if (value == null) {
                messages.send(player, "invalid-number");
                return true;
            }
            if (value < 0 || value >= session.draft().checkpoints().size()) {
                messages.send(player, "invalid-index");
                return true;
            }
            boolean changed = action.equals("set") ? editors.setCheckpoint(playerId, value) : editors.removeCheckpoint(playerId, value);
            if (!changed) {
                messages.send(player, action.equals("set") ? "selection-missing" : "invalid-index");
            } else {
                messages.send(player, action.equals("set") ? "edit-checkpoint-set" : "edit-checkpoint-removed", Map.of("index", String.valueOf(value + 1)));
            }
            return true;
        }
        if (action.equals("move")) {
            Integer from = args.length >= 5 ? index(args[3]) : null;
            Integer to = args.length >= 5 ? index(args[4]) : null;
            if (from == null || to == null) {
                messages.send(player, "invalid-number");
            } else if (from < 0 || to < 0 || from >= session.draft().checkpoints().size() || to >= session.draft().checkpoints().size()) {
                messages.send(player, "invalid-index");
            } else if (editors.moveCheckpoint(playerId, from, to)) {
                messages.send(player, "edit-checkpoint-moved", Map.of("from", String.valueOf(from + 1), "to", String.valueOf(to + 1)));
            }
            return true;
        }
        messages.send(player, "unknown-command");
        return true;
    }

    private boolean editSlot(Player player, String[] args) {
        if (args.length < 3) {
            messages.send(player, "unknown-command");
            return true;
        }
        UUID playerId = player.getUniqueId();
        if (args[2].equalsIgnoreCase("add")) {
            if (editors.addSlot(player)) {
                int value = editors.session(playerId).orElseThrow().draft().slots().size();
                messages.send(player, "edit-slot-added", Map.of("index", String.valueOf(value)));
            } else {
                messages.send(player, "selection-missing");
            }
            return true;
        }
        if (args[2].equalsIgnoreCase("remove")) {
            Integer value = args.length >= 4 ? index(args[3]) : null;
            EditorSession session = editors.session(playerId).orElseThrow();
            if (value == null) {
                messages.send(player, "invalid-number");
            } else if (value < 0 || value >= session.draft().slots().size() || !editors.removeSlot(playerId, value)) {
                messages.send(player, "invalid-index");
            } else {
                messages.send(player, "edit-slot-removed", Map.of("index", String.valueOf(value + 1)));
            }
            return true;
        }
        messages.send(player, "unknown-command");
        return true;
    }

    private boolean editPreview(Player player, String[] args) {
        if (args.length < 3 || (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off"))) {
            messages.send(player, "unknown-command");
            return true;
        }
        boolean enabled = args[2].equalsIgnoreCase("on");
        editors.setPreview(player.getUniqueId(), enabled);
        messages.send(player, "edit-preview", Map.of("state", enabled ? "开启" : "关闭"));
        return true;
    }

    private boolean editSave(Player player) {
        EditorSession session = editors.session(player.getUniqueId()).orElseThrow();
        if (races.isTrackInUse(session.draft().id())) {
            messages.send(player, "track-in-use");
        } else {
            editors.save(player);
        }
        return true;
    }

    private boolean editCancel(Player player) {
        editors.cancel(player.getUniqueId());
        messages.send(player, "edit-cancelled");
        return true;
    }

    private boolean force(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "unknown-command");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                races.startByAdmin(sender, args[2]);
                yield true;
            }
            case "laps" -> {
                if (args.length < 4) {
                    messages.send(sender, "unknown-command");
                    yield true;
                }
                Integer value = parseInteger(args[3]);
                if (value == null) {
                    messages.send(sender, "invalid-laps", Map.of("max", String.valueOf(RaceManager.MAX_FORMAL_LAPS)));
                } else {
                    races.configureLapsByAdmin(sender, args[2], value);
                }
                yield true;
            }
            case "cancel" -> {
                races.cancelByAdmin(sender, args[2]);
                yield true;
            }
            case "stoptrial" -> {
                String id = args[2].toLowerCase(Locale.ROOT);
                if (tracks.get(id).isEmpty()) {
                    messages.send(sender, "track-not-found", Map.of("track", id));
                } else {
                    int count = races.stopTrials(id);
                    messages.send(sender, "force-trial-stopped", Map.of("track", id, "count", String.valueOf(count)));
                }
                yield true;
            }
            case "delete-trial", "trial-delete" -> {
                yield deleteTrialRecord(sender, args, 2);
            }
            default -> {
                messages.send(sender, "unknown-command");
                yield true;
            }
        };
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "unknown-command");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "ban" -> adminBan(sender, args);
            case "unban" -> adminUnban(sender, args);
            default -> {
                messages.send(sender, "unknown-command");
                yield true;
            }
        };
    }

    private boolean adminBan(CommandSender sender, String[] args) {
        if (args.length < 4 || (!args[3].equalsIgnoreCase("true") && !args[3].equalsIgnoreCase("false"))) {
            messages.send(sender, "penalty-admin-ban-usage");
            return true;
        }
        String reference = args[2].trim();
        boolean announce = Boolean.parseBoolean(args[3]);
        resolveTarget(reference, targetId -> {
            if (targetId == null) {
                sendSender(sender, "penalty-target-not-found", Map.<String, String>of());
                return;
            }
            String playerName = plugin.penalties().displayName(targetId, reference);
            plugin.penalties().adminBan(targetId, playerName);
            plugin.scheduler().runGlobal(() -> {
                races.enforcePenalty(targetId);
                if (announce) {
                    Bukkit.broadcast(messages.unprefixed("admin-ban-announcement", Map.of("player", playerName)));
                }
            });
            sendSender(sender, "penalty-admin-ban-success", Map.of("player", playerName));
        });
        return true;
    }

    private boolean adminUnban(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "penalty-admin-unban-usage");
            return true;
        }
        String reference = args[2].trim();
        resolveTarget(reference, targetId -> {
            if (targetId == null) {
                sendSender(sender, "penalty-target-not-found", Map.<String, String>of());
                return;
            }
            String playerName = plugin.penalties().displayName(targetId, reference);
            boolean removed = plugin.penalties().unban(targetId);
            sendSender(sender, removed ? "penalty-admin-unban-success" : "penalty-admin-unban-not-found", Map.of("player", playerName));
        });
        return true;
    }

    private void resolveTarget(String reference, Consumer<UUID> callback) {
        UUID known = plugin.penalties().resolveReference(reference);
        if (known != null) {
            callback.accept(known);
            return;
        }
        plugin.scheduler().runGlobal(() -> {
            Player online = Bukkit.getPlayerExact(reference);
            if (online != null) {
                callback.accept(online.getUniqueId());
                return;
            }
            plugin.database().findPlayerIdByName(reference).whenComplete((resolved, failure) ->
                plugin.scheduler().runGlobal(() -> callback.accept(failure == null ? resolved : null))
            );
        });
    }

    private boolean reload(CommandSender sender) {
        if (admin(sender)) {
            plugin.reloadPluginSettings();
            messages.send(sender, "reload-complete");
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "player-only");
        return null;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("boatrace.admin")) {
            return true;
        }
        messages.send(sender, "no-permission");
        return false;
    }

    private static Integer index(String value) {
        try {
            return Integer.parseInt(value) - 1;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void reply(UUID playerId, boolean console, String key, Map<String, String> values) {
        if (playerId != null) {
            runForPlayer(playerId, owned -> messages.send(owned, key, values));
        } else if (console) {
            plugin.scheduler().runGlobal(() -> messages.send(Bukkit.getConsoleSender(), key, values));
        }
    }

    private void sendSender(CommandSender sender, String key, Map<String, String> values) {
        if (sender instanceof Player player) {
            runForPlayer(player.getUniqueId(), owned -> messages.send(owned, key, values));
        } else {
            plugin.scheduler().runGlobal(() -> messages.send(sender, key, values));
        }
    }

    private void runForPlayer(UUID playerId, Consumer<Player> action) {
        plugin.scheduler().runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.scheduler().runEntity(player, () -> {
                    Player owned = Bukkit.getPlayer(playerId);
                    if (owned != null) {
                        action.accept(owned);
                    }
                }, null);
            }
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            values.addAll(List.of("help", "gui", "create", "join", "laps", "leave", "start", "cancel", "status", "spec", "rank", "last"));
            if (sender.hasPermission("boatrace.admin")) {
            values.addAll(List.of("trial", "track", "edit", "force", "admin", "reload"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("gui")) {
            values.add("view");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("gui") && args[1].equalsIgnoreCase("view")) {
            if (plugin.guiConfigs() != null) {
                values.addAll(plugin.guiConfigs().snapshot().keySet());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("track")) {
            values.addAll(List.of("create", "edit", "list", "info", "delete"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("track") && List.of("edit", "info", "delete").contains(args[1].toLowerCase(Locale.ROOT))) {
            values.addAll(tracks.snapshot().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            values.addAll(List.of("pos1", "pos2", "start", "checkpoint", "slot", "preview", "save", "cancel"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("edit") && args[1].equalsIgnoreCase("checkpoint")) {
            values.addAll(List.of("add", "set", "remove", "move"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("edit") && args[1].equalsIgnoreCase("slot")) {
            values.addAll(List.of("add", "remove"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("edit") && args[1].equalsIgnoreCase("preview")) {
            values.addAll(List.of("on", "off"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("force")) {
            values.addAll(List.of("start", "laps", "cancel", "stoptrial", "delete-trial", "trial-delete"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("trial")) {
            values.add("delete");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            values.addAll(List.of("ban", "unban"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("ban") || args[1].equalsIgnoreCase("unban"))) {
            values.addAll(playerSuggestions());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("ban")) {
            values.addAll(List.of("true", "false"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("trial") && args[1].equalsIgnoreCase("delete")) {
            values.addAll(tracks.snapshot().keySet());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("trial") && args[1].equalsIgnoreCase("delete")) {
            values.addAll(playerSuggestions());
        } else if (args.length == 5 && args[0].equalsIgnoreCase("trial") && args[1].equalsIgnoreCase("delete")) {
            values.add("confirm");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("force") && (args[1].equalsIgnoreCase("stoptrial") || args[1].equalsIgnoreCase("delete-trial") || args[1].equalsIgnoreCase("trial-delete"))) {
            values.addAll(tracks.snapshot().keySet());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("force") && (args[1].equalsIgnoreCase("delete-trial") || args[1].equalsIgnoreCase("trial-delete"))) {
            values.addAll(playerSuggestions());
        } else if (args.length == 5 && args[0].equalsIgnoreCase("force") && (args[1].equalsIgnoreCase("delete-trial") || args[1].equalsIgnoreCase("trial-delete"))) {
            values.add("confirm");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("force") && (args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("laps") || args[1].equalsIgnoreCase("cancel"))) {
            values.addAll(races.sessionSnapshot().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            values.addAll(races.sessionSnapshot().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spec")) {
            values.addAll(List.of("join", "tp", "leave"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spec") && args[1].equalsIgnoreCase("join")) {
            values.addAll(races.sessionSnapshot().keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spec") && args[1].equalsIgnoreCase("tp") && sender instanceof Player player) {
            values.addAll(races.spectatorTargetNames(player));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("last")) {
            values.addAll(tracks.snapshot().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private List<String> playerSuggestions() {
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Bukkit.getOnlinePlayers().forEach(player -> addPlayerSuggestion(values, player.getName()));
        races.sessionSnapshot().values().forEach(session ->
            session.participants().forEach(participant -> addPlayerSuggestion(values, participant.playerName()))
        );
        plugin.penalties().snapshot().values().forEach(penalty -> addPlayerSuggestion(values, penalty.playerName()));
        tracks.snapshot().keySet().forEach(trackId ->
            leaderboards.top(trackId).forEach(record -> addPlayerSuggestion(values, record.playerName()))
        );
        return List.copyOf(values);
    }

    private static void addPlayerSuggestion(Set<String> values, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String name = candidate.trim();
        try {
            UUID.fromString(name);
            return;
        } catch (IllegalArgumentException ignored) {
        }
        values.add(name);
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
