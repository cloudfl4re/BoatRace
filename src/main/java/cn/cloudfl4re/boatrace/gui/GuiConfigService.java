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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loads and validates the user-editable GUI YAML files. */
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

    /** Reloads all .yml files currently present in the GUI directory. */
    public synchronized Map<String, GuiMenuConfig> reload() {
        ensureDefaults();
        Map<String, GuiMenuConfig> loaded = new LinkedHashMap<>();
        File[] files = directory.listFiles((dir, file) -> file.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                try {
                    String name = file.getName().substring(0, file.getName().length() - 4);
                    loaded.put(name, GuiMenuConfig.parse(name, YamlConfiguration.loadConfiguration(file)));
                } catch (RuntimeException ex) {
                    logger.log(Level.WARNING, "GUI 配置加载失败: " + file.getName() + "，已跳过", ex);
                }
            }
        }
        menus.clear();
        menus.putAll(loaded);
        return menus();
    }

    public synchronized GuiMenuConfig load(String name) {
        Objects.requireNonNull(name, "name");
        ensureDefaults();
        File file = new File(directory, name.endsWith(".yml") ? name : name + ".yml");
        if (!file.isFile()) {
            throw new IllegalArgumentException("GUI 配置不存在: " + name);
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

    /** Saves only button indexes, retaining all other user-defined fields. */
    public synchronized void saveLayout(String name, Map<String, Integer> indexes) throws IOException {
        Objects.requireNonNull(indexes, "indexes");
        ensureDefaults();
        File file = new File(directory, name.endsWith(".yml") ? name : name + ".yml");
        if (!file.isFile()) {
            throw new IOException("GUI 配置不存在: " + name);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int size = yaml.getInt("size", 54);
        for (Map.Entry<String, Integer> entry : indexes.entrySet()) {
            int slot = Objects.requireNonNull(entry.getValue(), "slot");
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("按钮槽位越界: " + slot);
            }
            yaml.set("menu." + entry.getKey() + ".index", slot);
        }
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
        load(name);
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
}
