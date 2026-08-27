package cn.cloudfl4re.boatrace.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MessageService {
    private final Plugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmpersand = LegacyComponentSerializer.legacyAmpersand();
    private final LegacyComponentSerializer legacySection = LegacyComponentSerializer.legacySection();
    private volatile YamlConfiguration messages;

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        var resource = plugin.getResource("messages.yml");
        if (resource != null) {
            try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Unable to load default BoatRace messages", exception);
            }
        }
        messages = loaded;
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component component(String key, Map<String, String> values) {
        String prefix = messages.getString("prefix", "<gray>[<aqua>BoatRace</aqua>]</gray> ");
        return render(prefix, key, values);
    }

    public Component unprefixed(String key) {
        return unprefixed(key, Map.of());
    }

    public Component unprefixed(String key, Map<String, String> values) {
        return render("", key, values);
    }

    private Component render(String prefix, String key, Map<String, String> values) {
        String raw = messages.getString(key, "<red>Missing message: " + key + "</red>");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", miniMessage.escapeTags(String.valueOf(entry.getValue())));
        }
        return deserialize(prefix).append(deserialize(raw));
    }

    public Component parse(String raw) {
        return deserialize(raw == null ? "" : raw);
    }

    private Component deserialize(String raw) {
        if (raw.indexOf('<') >= 0) {
            return miniMessage.deserialize(raw);
        }
        if (raw.indexOf('&') >= 0) {
            return legacyAmpersand.deserialize(raw);
        }
        if (raw.indexOf('§') >= 0) {
            return legacySection.deserialize(raw);
        }
        return Component.text(raw);
    }

    public void send(Audience audience, String key) {
        audience.sendMessage(component(key));
    }

    public void send(Audience audience, String key, Map<String, String> values) {
        audience.sendMessage(component(key, values));
    }
}
