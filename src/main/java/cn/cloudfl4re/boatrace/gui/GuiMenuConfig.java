package cn.cloudfl4re.boatrace.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GuiMenuConfig(String name, String title, int size, String permission,
                            Map<String, Button> buttons, Pane pane) {
    public GuiMenuConfig {
        buttons = Map.copyOf(buttons);
    }

    public static GuiMenuConfig parse(String name, YamlConfiguration yaml) {
        if (yaml == null) {
            throw new IllegalArgumentException("菜单配置不能为空");
        }
        int size = yaml.getInt("size", 54);
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("菜单 size 必须是 9–54 的 9 的倍数");
        }
        Map<String, Button> buttons = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("menu");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection item = section.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                int index = item.getInt("index", parseIndex(key));
                if (index < 0) {
                    throw new IllegalArgumentException("按钮槽位无效: " + key);
                }
                if (index >= size) {
                    throw new IllegalArgumentException("按钮槽位越界: " + index);
                }
                String materialName = item.getString("material", "");
                Material material = Material.matchMaterial(materialName);
                if (material == null || material.isAir()) {
                    throw new IllegalArgumentException("无效材质: " + materialName);
                }
                GuiAction action = GuiAction.parse(item.getString("action"));
                buttons.put(key, new Button(key, index, item.getString("name", key),
                    material, item.getStringList("lore"), action,
                    item.getString("permission", ""), item.getBoolean("confirm", false)));
            }
        }
        return new GuiMenuConfig(name, yaml.getString("title", name), size,
            yaml.getString("permission", ""), buttons,
            Pane.parse(yaml.getConfigurationSection("pane"), size));
    }

    private static int parseIndex(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public record Button(String key, int index, String name, Material material, List<String> lore,
                         GuiAction action, String permission, boolean confirm) {
        public Button {
            lore = List.copyOf(lore);
        }

        public int slot() {
            return index;
        }
    }

    public record Pane(boolean enabled, List<Integer> indexes, String name, Material material,
                       List<String> lore, boolean isEnchant, int customModelData,
                       boolean hideFlag, boolean hideEnchant) {
        public Pane {
            indexes = List.copyOf(indexes);
            lore = List.copyOf(lore);
        }

        static Pane parse(ConfigurationSection section) {
            return parse(section, Integer.MAX_VALUE);
        }

        static Pane parse(ConfigurationSection section, int menuSize) {
            if (section == null) {
                return new Pane(false, List.of(), "", Material.AIR, List.of(), false, 0, true, true);
            }
            List<Integer> indexes = new ArrayList<>();
            for (Object value : section.getList("index", List.of())) {
                int index;
                if (value instanceof Number number) {
                    index = number.intValue();
                } else {
                    try {
                        index = Integer.parseInt(String.valueOf(value).trim());
                    } catch (NumberFormatException ignored) {
                        throw new IllegalArgumentException("分隔板槽位无效: " + value);
                    }
                }
                if (index < 0 || index >= menuSize) {
                    throw new IllegalArgumentException("分隔板槽位越界: " + index);
                }
                indexes.add(index);
            }
            String materialName = section.getString("material", "");
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir()) {
                throw new IllegalArgumentException("无效分隔板材质");
            }
            return new Pane(section.getBoolean("enable", false), indexes,
                section.getString("name", ""), material, section.getStringList("lore"),
                section.getBoolean("isEnchant", false), section.getInt("custom-model-data", 0),
                section.getBoolean("hideFlag", true), section.getBoolean("hideEnchant", true));
        }
    }
}
