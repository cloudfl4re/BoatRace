package cn.cloudfl4re.boatrace.gui;

import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.BoatRacePlugin;
import cn.cloudfl4re.boatrace.service.RaceManager;
import cn.cloudfl4re.boatrace.scheduler.SchedulerFacade;
import cn.cloudfl4re.boatrace.model.RaceSession;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class GuiService {
    private final Plugin plugin;
    private final GuiConfigService configs;
    private final MessageService messages;
    private final RaceManager races;
    private final SchedulerFacade scheduler;
    private final GuiLayoutEditor layoutEditor;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
    private final Map<UUID, Consumer<String>> chatPrompts = new ConcurrentHashMap<>();

    public GuiService(Plugin plugin, GuiConfigService configs, MessageService messages, RaceManager races, SchedulerFacade scheduler) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.races = races;
        this.scheduler = scheduler;
        this.layoutEditor = new GuiLayoutEditor(plugin, configs);
    }

    public void open(Player player, String menu) {
        open(player, menu, false);
    }

    public void open(Player player, String menu, boolean editor) {
        GuiMenuConfig config = configs.get(menu);
        if (config == null || (!editor && !hasPermission(player, config.permission()))) {
            messages.send(player, "gui-invalid-menu");
            return;
        }
        GuiHolder holder = new GuiHolder(menu, player.getUniqueId(), editor);
        Inventory inventory = Bukkit.createInventory(holder, config.size(), legacy.serialize(miniMessage.deserialize(config.title())));
        holder.inventory(inventory);
        if (config.pane().enabled()) {
            ItemStack pane = item(config.pane().material(), config.pane().name(), config.pane().lore());
            for (int slot : config.pane().indexes()) if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, pane);
        }
        for (GuiMenuConfig.Button button : config.buttons().values()) {
            if (!editor && !hasPermission(player, button.permission())) continue;
            if (button.index() < inventory.getSize()) {
                ItemStack stack = item(button.material(), button.name(), button.lore());
                if (editor) {
                    ItemMeta editorMeta = stack.getItemMeta();
                    editorMeta.getPersistentDataContainer().set(layoutEditor.buttonKey(), org.bukkit.persistence.PersistentDataType.STRING, button.key());
                    stack.setItemMeta(editorMeta);
                }
                inventory.setItem(button.index(), stack);
            }
        }
        player.openInventory(inventory);
    }

    public GuiLayoutEditor layoutEditor() { return layoutEditor; }

    public void click(Player player, GuiHolder holder, int slot) {
        GuiMenuConfig config = configs.get(holder.menu());
        if (config == null) return;
        GuiMenuConfig.Button button = config.buttons().values().stream().filter(value -> value.index() == slot).findFirst().orElse(null);
        if (button == null || holder.layoutEditor()) return;
        if (!hasPermission(player, button.permission())) return;
        switch (button.action()) {
            case CLOSE -> player.closeInventory();
            case BACK -> open(player, "main");
            case OPEN_MAIN -> open(player, "main");
            case OPEN_RACE_CONTROL -> open(player, "race-control");
            case OPEN_TRACK_LIST -> open(player, "track-list");
            case OPEN_TRACK_EDITOR -> open(player, "track-editor");
            case RACE_STATUS -> races.status(player);
            case RACE_LEAVE -> races.leave(player);
            case RACE_START -> races.startByPlayer(player);
            case RACE_CANCEL -> races.cancelByPlayer(player);
            case RACE_CREATE -> races.createRoom(player);
            case RACE_JOIN -> prompt(player, "请输入比赛代码：", code -> races.joinRoom(player, code));
            case TRACK_CREATE -> promptTrack(player);
            case TRACK_LIST -> player.sendMessage("§b当前赛道：§f" + String.join("、", ((BoatRacePlugin) plugin).tracks().all().stream().map(track -> track.id()).toList()));
            case RACE_PAUSE -> control(player, "pause");
            case RACE_RESUME -> control(player, "resume");
            case RACE_END -> control(player, "end");
            default -> messages.send(player, "gui-action-success");
        }
    }

    public void acceptChat(Player player, String text) {
        Consumer<String> prompt = chatPrompts.remove(player.getUniqueId());
        if (prompt != null) scheduler.runEntity(player, () -> prompt.accept(text.trim()), null);
    }

    public boolean hasPrompt(UUID playerId) { return chatPrompts.containsKey(playerId); }

    private void prompt(Player player, String message, Consumer<String> action) {
        player.closeInventory();
        player.sendMessage("§e" + message + "§7输入 cancel 取消。 ");
        chatPrompts.put(player.getUniqueId(), value -> {
            if (value.equalsIgnoreCase("cancel")) return;
            action.accept(value);
        });
    }

    private void promptTrack(Player player) {
        prompt(player, "请输入新赛道 ID 和显示名称（用空格分隔）：", value -> {
            String[] parts = value.split("\\s+", 2);
            if (parts.length < 2) {
                messages.send(player, "invalid-number");
                return;
            }
            if (!((BoatRacePlugin) plugin).editors().beginCreate(player, parts[0].toLowerCase(java.util.Locale.ROOT), parts[1])) {
                messages.send(player, "track-exists", Map.of("track", parts[0]));
                return;
            }
            messages.send(player, "track-created", Map.of("track", parts[0]));
            open(player, "track-editor");
        });
    }

    private void control(Player player, String operation) {
        RaceSession session = races.sessionFor(player.getUniqueId());
        if (session == null) {
            messages.send(player, "gui-no-race");
            return;
        }
        boolean changed = switch (operation) {
            case "pause" -> races.pause(session.code());
            case "resume" -> races.resume(session.code());
            case "end" -> races.end(session.code());
            default -> false;
        };
        messages.send(player, changed ? (operation.equals("pause") ? "gui-race-paused" : operation.equals("resume") ? "gui-race-resumed" : "gui-race-ended") : "gui-action-success");
    }

    private boolean hasPermission(Player player, String permission) {
        return permission == null || permission.isBlank() || player.hasPermission(permission) || player.hasPermission("boatrace.admin");
    }

    private ItemStack item(Material material, String name, java.util.List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(legacy.serialize(miniMessage.deserialize(name == null ? "" : name)));
        meta.setLore(lore.stream().map(value -> legacy.serialize(miniMessage.deserialize(value))).toList());
        stack.setItemMeta(meta);
        return stack;
    }
}
