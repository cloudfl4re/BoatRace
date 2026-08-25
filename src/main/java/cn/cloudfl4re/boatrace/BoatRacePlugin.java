package cn.cloudfl4re.boatrace;

import cn.cloudfl4re.boatrace.command.RaceCommand;
import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.config.PluginSettings;
import cn.cloudfl4re.boatrace.listener.RaceListener;
import cn.cloudfl4re.boatrace.papi.BoatRaceExpansion;
import cn.cloudfl4re.boatrace.persistence.DatabaseService;
import cn.cloudfl4re.boatrace.scheduler.SchedulerFacade;
import cn.cloudfl4re.boatrace.service.EditorManager;
import cn.cloudfl4re.boatrace.service.LastRaceService;
import cn.cloudfl4re.boatrace.service.LeaderboardService;
import cn.cloudfl4re.boatrace.service.ParticleRenderer;
import cn.cloudfl4re.boatrace.service.RaceManager;
import cn.cloudfl4re.boatrace.service.TrackService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class BoatRacePlugin extends JavaPlugin {
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicReference<PluginSettings> settings = new AtomicReference<>();
    private SchedulerFacade scheduler;
    private MessageService messages;
    private DatabaseService database;
    private TrackService tracks;
    private LeaderboardService leaderboards;
    private LastRaceService lastRaces;
    private EditorManager editors;
    private RaceManager races;
    private BoatRaceExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        settings.set(PluginSettings.load(getConfig()));
        scheduler = new SchedulerFacade(this);
        messages = new MessageService(this);
        tracks = new TrackService();
        leaderboards = new LeaderboardService();
        lastRaces = new LastRaceService();
        database = new DatabaseService(getDataFolder().toPath(), settings.get().databaseQueueCapacity(), getLogger());
        ParticleRenderer particles = new ParticleRenderer(settings::get);
        races = new RaceManager(this, tracks, leaderboards, lastRaces, database, scheduler, messages, settings::get, particles);
        editors = new EditorManager(tracks, database, scheduler, messages, settings::get, particles);
        RaceCommand raceCommand = new RaceCommand(this);
        PluginCommand command = getCommand("race");
        if (command == null) {
            throw new IllegalStateException("race command is missing from plugin.yml");
        }
        command.setExecutor(raceCommand);
        command.setTabCompleter(raceCommand);
        getServer().getPluginManager().registerEvents(new RaceListener(races, editors), this);
        database.initialize().whenComplete((loaded, failure) -> {
            if (failure != null) {
                getLogger().log(Level.SEVERE, "BoatRace database initialization failed", failure);
                scheduler.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
                return;
            }
            scheduler.runGlobal(() -> {
                tracks.load(loaded.tracks());
                leaderboards.load(loaded.leaderboards());
                lastRaces.load(loaded.lastRaces());
                races.cleanupStaleBoats(loaded.ownedBoats());
                races.startCleanupTask();
                registerPlaceholderExpansion();
                ready.set(true);
                getLogger().info("BoatRace enabled for Folia/Paper 1.21.x+ (including 26.x)");
            });
        });
    }

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new BoatRaceExpansion(this, tracks, leaderboards, settings::get);
            if (!expansion.register()) {
                getLogger().warning("BoatRace PlaceholderAPI expansion registration failed");
            }
        }
    }

    public void reloadPluginSettings() {
        reloadConfig();
        settings.set(PluginSettings.load(getConfig()));
        messages.reload();
        races.startCleanupTask();
    }

    @Override
    public void onDisable() {
        ready.set(false);
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (editors != null) {
            editors.shutdown();
        }
        if (races != null) {
            races.shutdown();
        }
        if (scheduler != null) {
            scheduler.cancelPlatformTasks();
        }
        if (database != null) {
            database.close();
        }
    }

    public boolean isReady() {
        return ready.get();
    }

    public SchedulerFacade scheduler() {
        return scheduler;
    }

    public MessageService messages() {
        return messages;
    }

    public DatabaseService database() {
        return database;
    }

    public TrackService tracks() {
        return tracks;
    }

    public LeaderboardService leaderboards() {
        return leaderboards;
    }

    public LastRaceService lastRaces() {
        return lastRaces;
    }

    public EditorManager editors() {
        return editors;
    }

    public RaceManager races() {
        return races;
    }
}
