package cn.cloudfl4re.boatrace.gui;

import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.service.RaceManager;
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

public final class GuiService {
    private final Plugin plugin;
    private final GuiConfigService configs;
    private final MessageService messages;
    private final RaceManager races;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public GuiService(Plugin plugin, GuiConfigService configs, MessageService messages, RaceManager races) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.races = races;
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
            if (button.index() < inventory.getSize()) inventory.setItem(button.index(), item(button.material(), button.name(), button.lore()));
        }
        player.openInventory(inventory);
    }

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
            default -> messages.send(player, "gui-action-success");
        }
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
