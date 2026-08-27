package cn.cloudfl4re.boatrace.gui;

import cn.cloudfl4re.boatrace.BoatRacePlugin;
import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.model.EditorSession;
import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.RaceResultEntry;
import cn.cloudfl4re.boatrace.model.RaceSession;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.scheduler.SchedulerFacade;
import cn.cloudfl4re.boatrace.scheduler.TaskHandle;
import cn.cloudfl4re.boatrace.service.EditorManager;
import cn.cloudfl4re.boatrace.service.RaceManager;
import cn.cloudfl4re.boatrace.service.TrackValidator;
import cn.cloudfl4re.boatrace.util.TimeFormatter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiService {
    private static final long CONFIRMATION_TTL_NANOS = 10_000_000_000L;

    private final Plugin plugin;
    private final GuiConfigService configs;
    private final MessageService messages;
    private final RaceManager races;
    private final SchedulerFacade scheduler;
    private final GuiLayoutEditor layoutEditor;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
    private final ConcurrentHashMap<UUID, Prompt> chatPrompts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Confirmation> confirmations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> layoutLocks = new ConcurrentHashMap<>();
    private volatile TaskHandle cleanupTask = TaskHandle.NOOP;
    private volatile boolean shuttingDown;

    public GuiService(Plugin plugin, GuiConfigService configs, MessageService messages,
                      RaceManager races, SchedulerFacade scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.races = Objects.requireNonNull(races, "races");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layoutEditor = new GuiLayoutEditor(plugin, configs);
    }

    public void start() {
        shuttingDown = false;
        cleanupTask.cancel();
        cleanupTask = scheduler.runGlobalRepeating(this::purgeExpired, 20L, 20L);
    }

    public void open(Player player, String menu) {
        open(player, menu, false);
    }

    public void open(Player player, String menu, boolean editor) {
        if (shuttingDown || player == null) {
            return;
        }
        purgeExpired();
        String id = normalizeMenu(menu);
        GuiMenuConfig config = id == null ? null : configs.get(id);
        if (config == null) {
            messages.send(player, "gui-invalid-menu");
            return;
        }
        if (editor) {
            if (!hasLayoutPermission(player)) {
                messages.send(player, "gui-no-permission");
                return;
            }
            UUID owner = layoutLocks.putIfAbsent(id, player.getUniqueId());
            if (owner != null && !owner.equals(player.getUniqueId())) {
                messages.send(player, "gui-layout-locked");
                return;
            }
        } else if (!hasPermission(player, config.permission())) {
            messages.send(player, "gui-no-permission");
            return;
        }
        GuiHolder holder = new GuiHolder(id, player.getUniqueId(), editor);
        Inventory inventory = Bukkit.createInventory(holder, config.size(),
            legacy.serialize(messages.parse(config.title())));
        holder.inventory(inventory);
        if (config.pane().enabled()) {
            ItemStack pane = item(config.pane().material(), config.pane().name(), config.pane().lore(),
                config.pane().isEnchant(), config.pane().customModelData(),
                config.pane().hideFlag(), config.pane().hideEnchant());
            for (int slot : config.pane().indexes()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, pane.clone());
                }
            }
        }
        for (GuiMenuConfig.Button button : config.buttons().values()) {
            if (!editor && !hasPermission(player, button.permission())) {
                continue;
            }
            if (button.index() < 0 || button.index() >= inventory.getSize()) {
                continue;
            }
            ItemStack stack = item(button.material(), button.name(), button.lore(), false, 0, false, false);
            if (editor) {
                ItemMeta meta = stack.getItemMeta();
                meta.getPersistentDataContainer().set(layoutEditor.buttonKey(),
                    PersistentDataType.STRING, button.key());
                stack.setItemMeta(meta);
            }
            inventory.setItem(button.index(), stack);
        }
        player.openInventory(inventory);
    }

    public GuiLayoutEditor layoutEditor() {
        return layoutEditor;
    }

    public void click(Player player, GuiHolder holder, int slot) {
        if (player == null || holder == null || !holder.playerId().equals(player.getUniqueId()) || holder.layoutEditor()) {
            return;
        }
        GuiMenuConfig config = configs.get(holder.menu());
        if (config == null) {
            messages.send(player, "gui-invalid-menu");
            return;
        }
        GuiMenuConfig.Button button = config.buttons().values().stream()
            .filter(value -> value.index() == slot)
            .findFirst().orElse(null);
        if (button == null || !hasPermission(player, button.permission())) {
            return;
        }
        GuiAction action = button.action();
        if (button.confirm() || requiresConfirmation(action)) {
            requestConfirmation(player, action, holder.menu());
            return;
        }
        dispatch(player, action, null, holder.menu());
    }

    public void acceptChat(Player player, String text) {
        if (player == null) {
            return;
        }
        acceptChat(player.getUniqueId(), text);
    }

    public void acceptChat(UUID playerId, String text) {
        if (playerId == null || shuttingDown) {
            return;
        }
        Prompt prompt = chatPrompts.remove(playerId);
        if (prompt == null) {
            return;
        }
        String value = text == null ? "" : text.trim();
        runForPlayer(playerId, player -> handlePrompt(player, prompt, value));
    }

    public boolean hasPrompt(UUID playerId) {
        return playerId != null && chatPrompts.containsKey(playerId);
    }

    public void cleanup(UUID playerId) {
        if (playerId == null) {
            return;
        }
        chatPrompts.remove(playerId);
        confirmations.remove(playerId);
        layoutLocks.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
    }

    public void onClose(UUID playerId, String menu) {
        if (playerId != null && "confirm".equalsIgnoreCase(menu)) {
            confirmations.remove(playerId);
        }
    }

    public void shutdown() {
        shuttingDown = true;
        cleanupTask.cancel();
        cleanupTask = TaskHandle.NOOP;
        chatPrompts.clear();
        confirmations.clear();
        layoutLocks.clear();
        layoutEditor.shutdown();
    }

    public void saveLayoutAsync(Player player, GuiHolder holder, Inventory inventory) {
        if (player == null || holder == null || inventory == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Map<String, Integer> indexes = layoutEditor.snapshot(holder, inventory);
        layoutEditor.saveAsync(holder, indexes, scheduler, failure -> {
            layoutLocks.remove(holder.menu(), playerId);
            runForPlayer(playerId, owned -> messages.send(owned,
                failure == null ? "gui-layout-saved" : "gui-layout-save-failed"));
        });
    }

    private void dispatch(Player player, GuiAction action, String target, String currentMenu) {
        switch (action) {
            case CLOSE -> player.closeInventory();
            case BACK -> open(player, backMenu(currentMenu));
            case OPEN_MAIN -> open(player, "main");
            case OPEN_RACE_CONTROL -> open(player, "race-control");
            case OPEN_TRACK_LIST -> open(player, "track-list");
            case OPEN_TRACK_EDITOR -> openEditor(player);
            case OPEN_CONFIRM -> requestConfirmation(player, null, currentMenu);
            case RACE_CREATE -> races.createRoom(player);
            case RACE_JOIN -> prompt(player, new Prompt(PromptType.RACE_CODE, currentMenu, null), "gui-input-code");
            case RACE_START -> races.startByPlayer(player);
            case RACE_LEAVE -> races.leave(player);
            case RACE_CANCEL -> cancelRace(player, target);
            case RACE_STATUS -> showRaceStatus(player);
            case RACE_LAST -> showLastRace(player);
            case RACE_LEADERBOARD -> showLeaderboard(player);
            case RACE_PAUSE -> controlRace(player, target, Control.PAUSE);
            case RACE_RESUME -> controlRace(player, target, Control.RESUME);
            case RACE_END -> controlRace(player, target, Control.END);
            case TRACK_CREATE -> prompt(player, new Prompt(PromptType.TRACK_CREATE, currentMenu, null), "gui-input-track");
            case TRACK_LIST -> showTrackList(player);
            case TRACK_INFO -> withTrack(player, TrackOperation.INFO, currentMenu);
            case TRACK_EDIT -> withTrack(player, TrackOperation.EDIT, currentMenu);
            case TRACK_DELETE -> {
                if (target == null) {
                    withTrack(player, TrackOperation.DELETE, currentMenu);
                } else {
                    deleteTrack(player, target, currentMenu);
                }
            }
            case EDIT_POS1, EDIT_POS2 -> setEditorPosition(player, action == GuiAction.EDIT_POS1);
            case EDIT_START -> setEditorStart(player);
            case CHECKPOINT_ADD -> addCheckpoint(player);
            case CHECKPOINT_SET -> prompt(player, new Prompt(PromptType.CHECKPOINT_SET, currentMenu, null), "gui-input-index");
            case CHECKPOINT_REMOVE -> prompt(player, new Prompt(PromptType.CHECKPOINT_REMOVE, currentMenu, null), "gui-input-index");
            case CHECKPOINT_MOVE -> prompt(player, new Prompt(PromptType.CHECKPOINT_MOVE, currentMenu, null), "gui-input-move");
            case SLOT_ADD -> addSlot(player);
            case SLOT_REMOVE -> prompt(player, new Prompt(PromptType.SLOT_REMOVE, currentMenu, null), "gui-input-index");
            case PREVIEW_TOGGLE -> togglePreview(player);
            case EDIT_SAVE -> saveEditor(player);
            case EDIT_CANCEL -> cancelEditor(player);
            case CONFIRM -> confirmPending(player);
            case CANCEL_CONFIRM -> cancelConfirmation(player);
            default -> messages.send(player, "gui-action-unsupported");
        }
    }

    private void handlePrompt(Player player, Prompt prompt, String value) {
        if (value.isBlank() || value.equalsIgnoreCase("cancel")) {
            messages.send(player, "gui-input-cancelled");
            open(player, prompt.returnMenu());
            return;
        }
        switch (prompt.type()) {
            case RACE_CODE -> {
                races.joinRoom(player, value);
                open(player, "main");
            }
            case TRACK_CREATE -> handleTrackCreate(player, value);
            case TRACK_ID -> handleTrackId(player, prompt, value);
            case CHECKPOINT_SET, CHECKPOINT_REMOVE, SLOT_REMOVE -> handleIndex(player, prompt, value);
            case CHECKPOINT_MOVE -> handleMove(player, prompt, value);
        }
    }

    private void handleTrackCreate(Player player, String value) {
        String[] parts = value.split("\\s+", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            messages.send(player, "gui-input-track-invalid");
            open(player, "track-list");
            return;
        }
        String id = parts[0].toLowerCase(Locale.ROOT);
        if (!TrackValidator.validId(id)) {
            messages.send(player, "track-id-invalid");
            open(player, "track-list");
            return;
        }
        EditorManager editors = services().editors();
        if (!editors.beginCreate(player, id, parts[1].trim())) {
            messages.send(player, "track-exists", Map.of("track", id));
            open(player, "track-list");
            return;
        }
        messages.send(player, "track-created", Map.of("track", id));
        open(player, "track-editor", false);
    }

    private void handleTrackId(Player player, Prompt prompt, String value) {
        String id = value.toLowerCase(Locale.ROOT);
        Track track = services().tracks().get(id).orElse(null);
        if (track == null) {
            messages.send(player, "track-not-found", Map.of("track", id));
            open(player, prompt.returnMenu());
            return;
        }
        executeTrackOperation(player, prompt.trackOperation(), track, prompt.returnMenu());
    }

    private void handleIndex(Player player, Prompt prompt, String value) {
        Integer index = parseOneBasedIndex(value);
        EditorSession session = services().editors().session(player.getUniqueId()).orElse(null);
        if (index == null) {
            messages.send(player, "invalid-number");
            open(player, "track-editor", false);
            return;
        }
        if (session == null) {
            messages.send(player, "edit-no-session");
            return;
        }
        boolean changed;
        String key;
        if (prompt.type() == PromptType.SLOT_REMOVE) {
            changed = index >= 0 && index < session.draft().slots().size()
                && services().editors().removeSlot(player.getUniqueId(), index);
            key = "edit-slot-removed";
        } else if (prompt.type() == PromptType.CHECKPOINT_SET) {
            changed = index >= 0 && index < session.draft().checkpoints().size()
                && services().editors().setCheckpoint(player.getUniqueId(), index);
            key = "edit-checkpoint-set";
        } else {
            changed = index >= 0 && index < session.draft().checkpoints().size()
                && services().editors().removeCheckpoint(player.getUniqueId(), index);
            key = "edit-checkpoint-removed";
        }
        if (!changed) {
            messages.send(player, "invalid-index");
        } else {
            messages.send(player, key, Map.of("index", String.valueOf(index + 1)));
        }
        open(player, "track-editor", false);
    }

    private void handleMove(Player player, Prompt prompt, String value) {
        String[] parts = value.split("\\s+");
        if (parts.length < 2) {
            messages.send(player, "gui-input-move-invalid");
            open(player, "track-editor", false);
            return;
        }
        Integer from = parseOneBasedIndex(parts[0]);
        Integer to = parseOneBasedIndex(parts[1]);
        EditorSession session = services().editors().session(player.getUniqueId()).orElse(null);
        if (from == null || to == null || session == null
            || from < 0 || to < 0 || from >= session.draft().checkpoints().size()
            || to >= session.draft().checkpoints().size()
            || !services().editors().moveCheckpoint(player.getUniqueId(), from, to)) {
            messages.send(player, "invalid-index");
        } else {
            messages.send(player, "edit-checkpoint-moved", Map.of(
                "from", String.valueOf(from + 1), "to", String.valueOf(to + 1)));
        }
        open(player, "track-editor", false);
    }

    private void prompt(Player player, Prompt prompt, String messageKey) {
        chatPrompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        messages.send(player, messageKey);
    }

    private void showRaceStatus(Player player) {
        player.closeInventory();
        races.status(player);
    }

    private void showLeaderboard(Player player) {
        player.closeInventory();
        races.leaderboard(player);
    }

    private void showLastRace(Player player) {
        Track track = resolveTrack(player);
        if (track == null) {
            prompt(player, new Prompt(PromptType.TRACK_ID, "main", TrackOperation.LAST), "gui-input-track-id");
            return;
        }
        showLastRace(player, track);
    }

    private void showLastRace(Player player, Track track) {
        LastRace race = services().lastRaces().get(track.id()).orElse(null);
        player.closeInventory();
        if (race == null) {
            messages.send(player, "last-race-empty");
            return;
        }
        messages.send(player, "last-race-header", Map.of("track", track.displayName()));
        for (RaceResultEntry entry : race.entries()) {
            if (entry.finished()) {
                messages.send(player, "race-results-entry", Map.of(
                    "rank", String.valueOf(entry.rank()), "player", entry.playerName(),
                    "time", TimeFormatter.formatNanos(entry.elapsedNanos())));
            } else {
                messages.send(player, "race-results-dnf", Map.of(
                    "player", entry.playerName(), "laps", String.valueOf(entry.completedLaps()),
                    "total", String.valueOf(entry.totalLaps()),
                    "time", TimeFormatter.formatNanos(entry.elapsedNanos())));
            }
        }
    }

    private void showTrackList(Player player) {
        player.closeInventory();
        List<Track> tracks = services().tracks().all().stream()
            .sorted(Comparator.comparing(Track::id)).toList();
        if (tracks.isEmpty()) {
            messages.send(player, "track-list-empty");
            return;
        }
        messages.send(player, "track-list-header");
        for (Track track : tracks) {
            messages.send(player, "track-list-entry", Map.of("track", track.id(), "name", track.displayName()));
        }
    }

    private void withTrack(Player player, TrackOperation operation, String returnMenu) {
        Track track = resolveTrack(player);
        if (track == null) {
            prompt(player, new Prompt(PromptType.TRACK_ID, returnMenu, operation), "gui-input-track-id");
            return;
        }
        executeTrackOperation(player, operation, track, returnMenu);
    }

    private void executeTrackOperation(Player player, TrackOperation operation, Track track, String returnMenu) {
        switch (operation) {
            case INFO -> {
                player.closeInventory();
                messages.send(player, "track-info", Map.of(
                    "name", track.displayName(), "track", track.id(),
                    "checkpoints", String.valueOf(track.checkpoints().size()),
                    "slots", String.valueOf(track.slots().size())));
            }
            case LAST -> showLastRace(player, track);
            case EDIT -> {
                if (races.isTrackInUse(track.id())) {
                    messages.send(player, "track-in-use");
                } else if (!services().editors().beginEdit(player, track)) {
                    messages.send(player, "track-locked");
                } else {
                    messages.send(player, "track-editing", Map.of("track", track.id()));
                    open(player, "track-editor", false);
                }
            }
            case DELETE -> requestConfirmation(player, GuiAction.TRACK_DELETE, returnMenu, track.id());
        }
    }

    private void setEditorPosition(Player player, boolean first) {
        if (!services().editors().setPosition(player, first)) {
            messages.send(player, "edit-no-session");
        } else {
            messages.send(player, first ? "selection-pos1" : "selection-pos2");
        }
        open(player, "track-editor", false);
    }

    private void setEditorStart(Player player) {
        messages.send(player, services().editors().setStart(player.getUniqueId())
            ? "edit-start-set" : "selection-missing");
        open(player, "track-editor", false);
    }

    private void addCheckpoint(Player player) {
        boolean changed = services().editors().addCheckpoint(player.getUniqueId());
        messages.send(player, changed ? "edit-checkpoint-added" : "selection-missing",
            changed ? Map.of("index", String.valueOf(services().editors().session(player.getUniqueId())
                .map(value -> value.draft().checkpoints().size()).orElse(0))) : Map.of());
        open(player, "track-editor", false);
    }

    private void addSlot(Player player) {
        boolean changed = services().editors().addSlot(player);
        messages.send(player, changed ? "edit-slot-added" : "selection-missing",
            changed ? Map.of("index", String.valueOf(services().editors().session(player.getUniqueId())
                .map(value -> value.draft().slots().size()).orElse(0))) : Map.of());
        open(player, "track-editor", false);
    }

    private void togglePreview(Player player) {
        EditorSession session = services().editors().session(player.getUniqueId()).orElse(null);
        if (session == null) {
            messages.send(player, "edit-no-session");
            return;
        }
        boolean enabled = !session.preview();
        services().editors().setPreview(player.getUniqueId(), enabled);
        messages.send(player, "edit-preview", Map.of("state", enabled ? "开启" : "关闭"));
        open(player, "track-editor", false);
    }

    private void saveEditor(Player player) {
        if (services().editors().session(player.getUniqueId()).isEmpty()) {
            messages.send(player, "edit-no-session");
            return;
        }
        player.closeInventory();
        services().editors().save(player);
    }

    private void cancelEditor(Player player) {
        boolean changed = services().editors().cancel(player.getUniqueId());
        player.closeInventory();
        messages.send(player, changed ? "edit-cancelled" : "edit-no-session");
    }

    private void cancelRace(Player player, String target) {
        if (target != null && player.hasPermission("boatrace.admin")) {
            races.cancelByAdmin(player, target);
        } else {
            races.cancelByPlayer(player);
        }
        player.closeInventory();
    }

    private void controlRace(Player player, String target, Control control) {
        String code = target;
        if (code == null) {
            RaceSession session = races.sessionFor(player.getUniqueId());
            code = session == null ? null : session.code();
        }
        if (code == null) {
            messages.send(player, "gui-no-race");
            return;
        }
        boolean changed = switch (control) {
            case PAUSE -> races.pause(code);
            case RESUME -> races.resume(code);
            case END -> races.end(code);
        };
        if (!changed) {
            messages.send(player, "gui-action-failed");
            return;
        }
        messages.send(player, switch (control) {
            case PAUSE -> "gui-race-paused";
            case RESUME -> "gui-race-resumed";
            case END -> "gui-race-ended";
        });
        player.closeInventory();
    }

    private void openEditor(Player player) {
        if (services().editors().session(player.getUniqueId()).isEmpty()) {
            messages.send(player, "edit-no-session");
            return;
        }
        open(player, "track-editor", false);
    }

    private void requestConfirmation(Player player, GuiAction action, String returnMenu) {
        String target = targetFor(player, action);
        if (target == null) {
            messages.send(player, action == GuiAction.TRACK_DELETE ? "gui-no-track" : "gui-no-race");
            return;
        }
        requestConfirmation(player, action, returnMenu, target);
    }

    private void requestConfirmation(Player player, GuiAction action, String returnMenu, String target) {
        if (action == null || target == null || target.isBlank()) {
            messages.send(player, "gui-confirm-invalid");
            return;
        }
        confirmations.put(player.getUniqueId(), new Confirmation(action, target,
            returnMenu == null ? "main" : returnMenu, System.nanoTime() + CONFIRMATION_TTL_NANOS));
        open(player, "confirm");
    }

    private void confirmPending(Player player) {
        Confirmation confirmation = confirmations.remove(player.getUniqueId());
        if (confirmation == null || confirmation.expiresAtNanos() <= System.nanoTime()) {
            messages.send(player, "gui-confirm-expired");
            return;
        }
        dispatch(player, confirmation.action(), confirmation.target(), confirmation.returnMenu());
    }

    private void cancelConfirmation(Player player) {
        Confirmation confirmation = confirmations.remove(player.getUniqueId());
        messages.send(player, confirmation == null ? "gui-confirm-invalid" : "gui-confirm-cancelled");
        open(player, confirmation == null ? "main" : confirmation.returnMenu());
    }

    private String targetFor(Player player, GuiAction action) {
        if (action == GuiAction.TRACK_DELETE) {
            Track track = resolveTrack(player);
            return track == null ? null : track.id();
        }
        if (action == GuiAction.EDIT_CANCEL) {
            return services().editors().session(player.getUniqueId()).map(value -> value.draft().id()).orElse(null);
        }
        RaceSession session = races.sessionFor(player.getUniqueId());
        return session == null ? null : session.code();
    }

    private void deleteTrack(Player player, String trackId, String returnMenu) {
        if (services().tracks().get(trackId).isEmpty()) {
            messages.send(player, "track-not-found", Map.of("track", trackId));
            return;
        }
        if (races.isTrackInUse(trackId) || services().editors().isLocked(trackId)) {
            messages.send(player, "track-in-use");
            return;
        }
        UUID playerId = player.getUniqueId();
        services().database().deleteTrack(trackId).whenComplete((ignored, failure) ->
            scheduler.runGlobal(() -> {
                if (failure != null) {
                    plugin.getLogger().warning("BoatRace GUI track delete failed: " + failure.getMessage());
                    runForPlayer(playerId, owned -> messages.send(owned, "database-error"));
                    return;
                }
                services().tracks().remove(trackId);
                services().leaderboards().remove(trackId);
                services().lastRaces().remove(trackId);
                runForPlayer(playerId, owned -> {
                    messages.send(owned, "track-deleted", Map.of("track", trackId));
                    open(owned, returnMenu == null ? "track-list" : returnMenu);
                });
            }));
    }

    private void purgeExpired() {
        long now = System.nanoTime();
        confirmations.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
    }

    private Track resolveTrack(Player player) {
        RaceSession session = races.sessionFor(player.getUniqueId());
        if (session != null) {
            return services().tracks().get(session.trackId()).orElse(null);
        }
        if (player.getWorld() == null) {
            return null;
        }
        Point3 point = new Point3(player.getX(), player.getY(), player.getZ());
        return services().tracks().atStart(player.getWorld().getUID(), point).orElse(null);
    }

    private void runForPlayer(UUID playerId, java.util.function.Consumer<Player> action) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return;
            }
            scheduler.runEntity(player, () -> {
                Player owned = Bukkit.getPlayer(playerId);
                if (owned != null && !shuttingDown) {
                    action.accept(owned);
                }
            }, null);
        });
    }

    private ItemStack item(Material material, String name, List<String> lore, boolean enchanted,
                           int customModelData, boolean hideFlag, boolean hideEnchant) {
        ItemStack stack = new ItemStack(material == null || material == Material.AIR ? Material.STONE : material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(legacy.serialize(messages.parse(name == null ? "" : name)));
        meta.setLore((lore == null ? List.<String>of() : lore).stream()
            .map(value -> legacy.serialize(messages.parse(value))).toList());
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (enchanted) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        if (hideFlag) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        if (hideEnchant) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean hasPermission(Player player, String permission) {
        if (permission == null || permission.isBlank() || player.hasPermission(permission)) {
            return true;
        }
        return player.hasPermission("boatrace.admin") && isAdminGuiPermission(permission);
    }

    private boolean hasLayoutPermission(Player player) {
        return player.hasPermission("boatrace.gui.view") || player.hasPermission("boatrace.admin");
    }

    private static boolean isAdminGuiPermission(String permission) {
        return permission.equals("boatrace.gui.control")
            || permission.equals("boatrace.gui.track")
            || permission.equals("boatrace.gui.view");
    }

    private static boolean requiresConfirmation(GuiAction action) {
        return action == GuiAction.RACE_END || action == GuiAction.RACE_CANCEL
            || action == GuiAction.TRACK_DELETE || action == GuiAction.EDIT_CANCEL;
    }

    private static String normalizeMenu(String menu) {
        if (menu == null) {
            return null;
        }
        String value = menu.trim().toLowerCase(Locale.ROOT);
        return value.endsWith(".yml") ? value.substring(0, value.length() - 4) : value;
    }

    private static String backMenu(String currentMenu) {
        if ("race-control".equalsIgnoreCase(currentMenu) || "track-list".equalsIgnoreCase(currentMenu)) {
            return "main";
        }
        if ("track-editor".equalsIgnoreCase(currentMenu)) {
            return "track-list";
        }
        return "main";
    }

    private static Integer parseOneBasedIndex(String value) {
        try {
            return Integer.parseInt(value.trim()) - 1;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private BoatRacePlugin services() {
        if (!(plugin instanceof BoatRacePlugin value)) {
            throw new IllegalStateException("GuiService requires BoatRacePlugin");
        }
        return value;
    }

    private enum PromptType { RACE_CODE, TRACK_CREATE, TRACK_ID, CHECKPOINT_SET, CHECKPOINT_REMOVE, CHECKPOINT_MOVE, SLOT_REMOVE }
    private enum TrackOperation { INFO, LAST, EDIT, DELETE }
    private enum Control { PAUSE, RESUME, END }
    private record Prompt(PromptType type, String returnMenu, TrackOperation trackOperation) { }
    private record Confirmation(GuiAction action, String target, String returnMenu, long expiresAtNanos) { }
}

