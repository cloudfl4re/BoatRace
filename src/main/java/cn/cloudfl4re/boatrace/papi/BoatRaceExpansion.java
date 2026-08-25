package cn.cloudfl4re.boatrace.papi;

import cn.cloudfl4re.boatrace.config.PluginSettings;
import cn.cloudfl4re.boatrace.model.TrialRecord;
import cn.cloudfl4re.boatrace.service.LeaderboardService;
import cn.cloudfl4re.boatrace.service.TrackService;
import cn.cloudfl4re.boatrace.util.TimeFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

public final class BoatRaceExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final TrackService tracks;
    private final LeaderboardService leaderboards;
    private final Supplier<PluginSettings> settings;

    public BoatRaceExpansion(Plugin plugin, TrackService tracks, LeaderboardService leaderboards, Supplier<PluginSettings> settings) {
        this.plugin = plugin;
        this.tracks = tracks;
        this.leaderboards = leaderboards;
        this.settings = settings;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "boatrace";
    }

    @Override
    public @NotNull String getAuthor() {
        return "cloudfl4re";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        Optional<PapiRequest> parsed = PapiParser.parse(params);
        if (parsed.isEmpty() || tracks.get(parsed.get().trackId()).isEmpty()) {
            return null;
        }
        PapiRequest request = parsed.get();
        Optional<TrialRecord> record = leaderboards.at(request.trackId(), request.rank());
        PluginSettings current = settings.get();
        if (record.isEmpty()) {
            return switch (request.field()) {
                case NAME -> current.papiEmptyName();
                case TIME -> current.papiEmptyTime();
                case LINE -> request.rank() + ". " + current.papiEmptyName();
            };
        }
        TrialRecord value = record.get();
        String time = TimeFormatter.formatNanos(value.bestNanos());
        return switch (request.field()) {
            case NAME -> value.playerName();
            case TIME -> time;
            case LINE -> request.rank() + ". " + value.playerName() + " - " + time;
        };
    }
}
