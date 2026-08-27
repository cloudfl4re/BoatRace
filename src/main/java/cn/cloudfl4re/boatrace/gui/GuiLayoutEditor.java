package cn.cloudfl4re.boatrace.gui;

import cn.cloudfl4re.boatrace.scheduler.SchedulerFacade;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class GuiLayoutEditor {
    private final Plugin plugin;
    private final GuiConfigService configs;
    private final NamespacedKey buttonKey;

    public GuiLayoutEditor(Plugin plugin, GuiConfigService configs) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.buttonKey = new NamespacedKey(plugin, "gui_button");
    }

    public NamespacedKey buttonKey() {
        return buttonKey;
    }

    public Map<String, Integer> snapshot(GuiHolder holder, Inventory inventory) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(inventory, "inventory");
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            String key = item.getItemMeta().getPersistentDataContainer()
                .get(buttonKey, PersistentDataType.STRING);
            if (key != null && !key.isBlank()) {
                indexes.put(key, slot);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(indexes));
    }

    public void save(Player player, GuiHolder holder, Inventory inventory) throws IOException {
        save(holder, snapshot(holder, inventory));
    }

    public void save(GuiHolder holder, Map<String, Integer> indexes) throws IOException {
        configs.saveLayout(holder.menu(), indexes);
    }

    public void saveAsync(GuiHolder holder, Map<String, Integer> indexes,
                          SchedulerFacade scheduler, Consumer<Throwable> completion) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(completion, "completion");
        Map<String, Integer> copy = Map.copyOf(indexes);
        scheduler.runAsync(() -> {
            Throwable failure = null;
            try {
                save(holder, copy);
            } catch (Throwable throwable) {
                failure = throwable;
            }
            Throwable result = failure;
            scheduler.runGlobal(() -> completion.accept(result));
        });
    }

    public static Map<String, Integer> move(Map<String, Integer> source, String key, int slot, int size) {
        if (source == null || !source.containsKey(key)) {
            throw new IllegalArgumentException("按钮不存在: " + key);
        }
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("按钮槽位越界: " + slot);
        }
        Map<String, Integer> result = new LinkedHashMap<>(source);
        result.put(key, slot);
        return result;
    }

    public void shutdown() {
    }
}
