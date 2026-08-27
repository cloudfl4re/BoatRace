package cn.cloudfl4re.boatrace;

import cn.cloudfl4re.boatrace.command.RaceCommand;
import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.config.PluginSettings;
import cn.cloudfl4re.boatrace.gui.GuiConfigService;
import cn.cloudfl4re.boatrace.gui.GuiListener;
import cn.cloudfl4re.boatrace.gui.GuiService;
import cn.cloudfl4re.boatrace.listener.RaceListener;
import cn.cloudfl4re.boatrace.papi.BoatRaceExpansion;
import cn.cloudfl4re.boatrace.persistence.DatabaseService;
import cn.cloudfl4re.boatrace.scheduler.SchedulerFacade;
import cn.cloudfl4re.boatrace.scheduler.TaskHandle;
import cn.cloudfl4re.boatrace.service.EditorManager;
import cn.cloudfl4re.boatrace.service.LastRaceService;
import cn.cloudfl4re.boatrace.service.LeaderboardService;
import cn.cloudfl4re.boatrace.service.ParticleRenderer;
import cn.cloudfl4re.boatrace.service.PenaltyService;
import cn.cloudfl4re.boatrace.service.PersonalStatsService;
import cn.cloudfl4re.boatrace.service.RaceManager;
import cn.cloudfl4re.boatrace.service.TrackService;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import java.util.logging.Level;

public final class BoatRacePlugin extends JavaPlugin {
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicReference<PluginSettings> settings = new AtomicReference<>();
    private SchedulerFacade scheduler;
    private MessageService messages;
    private DatabaseService database;
    private PenaltyService penalties;
    private PersonalStatsService personalStats;
    private TrackService tracks;
    private LeaderboardService leaderboards;
    private LastRaceService lastRaces;
    private EditorManager editors;
    private RaceManager races;
    private GuiConfigService guiConfigs;
    private GuiService gui;
    private BoatRaceExpansion expansion;
    private volatile TaskHandle placeholderCheckTask = TaskHandle.NOOP;

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
        var initialization = database.initialize();
        penalties = new PenaltyService(database, getLogger());
        personalStats = new PersonalStatsService(database, getLogger());
        ParticleRenderer particles = new ParticleRenderer(settings::get);
        races = new RaceManager(this, tracks, leaderboards, lastRaces, database, penalties, personalStats, scheduler, messages, settings::get, particles);
        editors = new EditorManager(tracks, database, scheduler, messages, settings::get, particles);
        guiConfigs = new GuiConfigService(this);
        guiConfigs.initialize();
        gui = new GuiService(this, guiConfigs, messages, races, scheduler);
        registerPlaceholderExpansion();
        placeholderCheckTask = scheduler.runGlobalRepeating(this::registerPlaceholderExpansion, 1L, 5L);
        RaceCommand raceCommand = new RaceCommand(this);
        PluginCommand command = getCommand("race");
        if (command == null) {
            throw new IllegalStateException("race command is missing from plugin.yml");
        }
        command.setExecutor(raceCommand);
        command.setTabCompleter(raceCommand);
        getServer().getPluginManager().registerEvents(new RaceListener(races, editors), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gui), this);
        initialization.whenComplete((loaded, failure) -> {
            if (failure != null) {
                getLogger().log(Level.SEVERE, "BoatRace database initialization failed", failure);
                scheduler.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
                return;
            }
            scheduler.runGlobal(() -> {
                tracks.load(loaded.tracks());
                leaderboards.load(loaded.leaderboards(), loaded.leaderboardRecordCounts());
                lastRaces.load(loaded.lastRaces());
                penalties.load(loaded.penalties());
                races.cleanupStaleBoats(loaded.ownedBoats());
                races.startCleanupTask();
                ready.set(true);
                getLogger().info("BoatRace enabled for API " + getDescription().getAPIVersion());
            });
        });
    }

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        if (expansion == null) {
            expansion = new BoatRaceExpansion(this, leaderboards, lastRaces, races, personalStats, penalties, settings::get);
        }
        PlaceholderAPIPlugin papi = PlaceholderAPIPlugin.getInstance();
        if (papi == null || !papi.isEnabled()) {
            return;
        }
        PlaceholderExpansion registered = papi.getLocalExpansionManager().getExpansion(expansion.getIdentifier());
        if (registered == expansion) {
            return;
        }
        if (registered != null) {
            if (!registered.getClass().getName().equals(BoatRaceExpansion.class.getName())
                || !Objects.equals(registered.getAuthor(), expansion.getAuthor())) {
                getLogger().warning("PlaceholderAPI identifier 'boatrace' is already owned by another expansion");
                return;
            }
            registered.unregister();
        }
        if (!expansion.register()) {
            getLogger().warning("BoatRace PlaceholderAPI expansion registration failed");
        } else {
            getLogger().info("BoatRace PlaceholderAPI expansion registered");
        }
    }

    public void reloadPluginSettings() {
        reloadConfig();
        settings.set(PluginSettings.load(getConfig()));
        messages.reload();
        if (guiConfigs != null) {
            guiConfigs.reload();
        }
        races.startCleanupTask();
    }

    @Override
    public void onDisable() {
        ready.set(false);
        placeholderCheckTask.cancel();
        placeholderCheckTask = TaskHandle.NOOP;
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (gui != null) {
            gui = null;
        }
        guiConfigs = null;
        if (editors != null) {
            editors.shutdown();
        }
        if (races != null) {
            races.shutdown();
        }
        if (personalStats != null) {
            personalStats.shutdown();
        }
        if (penalties != null) {
            penalties.shutdown();
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

    public PenaltyService penalties() {
        return penalties;
    }

    public PersonalStatsService personalStats() {
        return personalStats;
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

    public GuiConfigService guiConfigs() {
        return guiConfigs;
    }

    public GuiService gui() {
        return gui;
    }
}
