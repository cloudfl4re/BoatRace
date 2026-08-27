package cn.cloudfl4re.boatrace.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GuiConfigService {
    private final Plugin plugin;
    private final File directory;
    private final Logger logger;
    private final Map<String, GuiMenuConfig> menus = new LinkedHashMap<>();

    public GuiConfigService(Plugin plugin) {
        this(Objects.requireNonNull(plugin, "plugin").getDataFolder(), plugin);
    }

    public GuiConfigService(File dataFolder) {
        this(dataFolder, null);
    }

    public GuiConfigService(File dataFolder, Plugin plugin) {
        this.directory = new File(Objects.requireNonNull(dataFolder, "dataFolder"), "gui");
        this.plugin = plugin;
        this.logger = plugin == null ? Logger.getLogger(GuiConfigService.class.getName()) : plugin.getLogger();
    }

    public synchronized Map<String, GuiMenuConfig> reload() {
        ensureDefaults();
        Map<String, GuiMenuConfig> loaded = new LinkedHashMap<>();
        File[] files = directory.listFiles((dir, file) -> file.endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                try {
                    String name = file.getName().substring(0, file.getName().length() - 4)
                        .toLowerCase(Locale.ROOT);
                    validateName(name);
                    loaded.put(name, GuiMenuConfig.parseLenient(name,
                        YamlConfiguration.loadConfiguration(file),
                        detail -> logger.warning("GUI 菜单 " + name + " 已跳过按钮: " + detail)));
                } catch (RuntimeException ex) {
                    logger.log(Level.WARNING, "GUI 配置加载失败: " + file.getName() + "，已跳过", ex);
                }
            }
        }
        menus.clear();
        menus.putAll(loaded);
        return menus();
    }

    public synchronized void initialize() {
        reload();
    }

    public synchronized Map<String, GuiMenuConfig> snapshot() {
        return menus();
    }

    public synchronized GuiMenuConfig load(String name) {
        String normalized = normalizeName(name);
        ensureDefaults();
        File file = new File(directory, normalized + ".yml");
        if (!file.isFile()) {
            throw new IllegalArgumentException("GUI 配置不存在: " + normalized);
        }
        String id = file.getName().substring(0, file.getName().length() - 4);
        GuiMenuConfig config = GuiMenuConfig.parse(id, YamlConfiguration.loadConfiguration(file));
        menus.put(id, config);
        return config;
    }

    public synchronized GuiMenuConfig get(String name) {
        return menus.get(name);
    }

    public synchronized Map<String, GuiMenuConfig> menus() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(menus));
    }

    public synchronized void saveLayout(String name, Map<String, Integer> indexes) throws IOException {
        Objects.requireNonNull(indexes, "indexes");
        String normalized = normalizeName(name);
        ensureDefaults();
        File file = new File(directory, normalized + ".yml");
        if (!file.isFile()) {
            throw new IOException("GUI 配置不存在: " + normalized);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int size = yaml.getInt("size", 54);
        for (Map.Entry<String, Integer> entry : indexes.entrySet()) {
            if (yaml.getConfigurationSection("menu." + entry.getKey()) == null) {
                continue;
            }
            int slot = Objects.requireNonNull(entry.getValue(), "slot");
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("按钮槽位越界: " + slot);
            }
            yaml.set("menu." + entry.getKey() + ".index", slot);
        }
        GuiMenuConfig parsed = GuiMenuConfig.parseLenient(normalized, yaml);
        Path target = file.toPath();
        Path temp = Files.createTempFile(directory.toPath(), file.getName(), ".tmp");
        try {
            yaml.save(temp.toFile());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        menus.put(normalized, parsed);
    }

    private void ensureDefaults() {
        if (!directory.exists() && !directory.mkdirs()) {
            logger.warning("无法创建 GUI 配置目录: " + directory);
        }
        if (plugin == null) {
            return;
        }
        for (String resource : new String[]{"main.yml", "race-control.yml", "track-list.yml", "track-editor.yml", "confirm.yml"}) {
            File target = new File(directory, resource);
            if (target.exists()) {
                continue;
            }
            try (InputStream in = plugin.getResource("gui/" + resource)) {
                if (in != null) {
                    Files.copy(in, target.toPath());
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "无法生成默认 GUI 配置: " + resource, ex);
            }
        }
    }

    private static String normalizeName(String raw) {
        Objects.requireNonNull(raw, "name");
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith(".yml")) {
            value = value.substring(0, value.length() - 4);
        }
        validateName(value);
        return value;
    }

    private static void validateName(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("GUI 菜单名称无效: " + value);
        }
    }
}
