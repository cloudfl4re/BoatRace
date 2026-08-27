package cn.cloudfl4re.boatrace.gui;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuiLayoutEditor {
    private final Plugin plugin;
    private final GuiConfigService configs;
    private final NamespacedKey buttonKey;

    public GuiLayoutEditor(Plugin plugin, GuiConfigService configs) {
        this.plugin = plugin;
        this.configs = configs;
        this.buttonKey = new NamespacedKey(plugin, "gui_button");
    }

    public NamespacedKey buttonKey() { return buttonKey; }

    public void save(Player player, GuiHolder holder, Inventory inventory) throws IOException {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            String key = item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING);
            if (key != null) indexes.put(key, slot);
        }
        configs.saveLayout(holder.menu(), indexes);
    }

    public static Map<String, Integer> move(Map<String, Integer> source, String key, int slot, int size) {
        if (!source.containsKey(key)) throw new IllegalArgumentException("按钮不存在: " + key);
        if (slot < 0 || slot >= size) throw new IllegalArgumentException("按钮槽位越界: " + slot);
        Map<String, Integer> result = new LinkedHashMap<>(source);
        result.put(key, slot);
        return result;
    }
}
