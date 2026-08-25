package cn.cloudfl4re.boatrace.scheduler;

import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class SchedulerFacade {
    private static Boolean folia;
    private final Plugin plugin;
    private final Server server;

    public SchedulerFacade(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.server = plugin.getServer();
        detectFolia();
    }

    private static synchronized void detectFolia() {
        if (folia == null) {
            folia = ServerBuildInfo.buildInfo().isBrandCompatible(ServerBuildInfo.BRAND_FOLIA_ID);
        }
    }

    public static boolean isFolia() {
        if (folia == null) {
            throw new IllegalStateException("Folia detection has not run");
        }
        return folia;
    }

    public TaskHandle runEntity(Entity entity, Runnable runnable, Runnable retired) {
        if (isFolia()) {
            ScheduledTask task = entity.getScheduler().run(plugin, ignored -> runnable.run(), retired);
            return task == null ? TaskHandle.NOOP : task::cancel;
        }
        BukkitTask task = server.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    public TaskHandle runEntityDelayed(Entity entity, Runnable runnable, Runnable retired, long ticks) {
        long delay = Math.max(1L, ticks);
        if (isFolia()) {
            ScheduledTask task = entity.getScheduler().runDelayed(plugin, ignored -> runnable.run(), retired, delay);
            return task == null ? TaskHandle.NOOP : task::cancel;
        }
        BukkitTask task = server.getScheduler().runTaskLater(plugin, runnable, delay);
        return task::cancel;
    }

    public TaskHandle runEntityRepeating(Entity entity, Runnable runnable, Runnable retired, long initialTicks, long periodTicks) {
        long initial = Math.max(1L, initialTicks);
        long period = Math.max(1L, periodTicks);
        if (isFolia()) {
            ScheduledTask task = entity.getScheduler().runAtFixedRate(plugin, ignored -> runnable.run(), retired, initial, period);
            return task == null ? TaskHandle.NOOP : task::cancel;
        }
        BukkitTask task = server.getScheduler().runTaskTimer(plugin, runnable, initial, period);
        return task::cancel;
    }

    public TaskHandle runRegion(Location location, Runnable runnable) {
        if (isFolia()) {
            ScheduledTask task = server.getRegionScheduler().run(plugin, location, ignored -> runnable.run());
            return task::cancel;
        }
        BukkitTask task = server.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    public TaskHandle runGlobal(Runnable runnable) {
        if (isFolia()) {
            ScheduledTask task = server.getGlobalRegionScheduler().run(plugin, ignored -> runnable.run());
            return task::cancel;
        }
        BukkitTask task = server.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    public TaskHandle runGlobalDelayed(Runnable runnable, long ticks) {
        long delay = Math.max(1L, ticks);
        if (isFolia()) {
            ScheduledTask task = server.getGlobalRegionScheduler().runDelayed(plugin, ignored -> runnable.run(), delay);
            return task::cancel;
        }
        BukkitTask task = server.getScheduler().runTaskLater(plugin, runnable, delay);
        return task::cancel;
    }

    public TaskHandle runGlobalRepeating(Runnable runnable, long initialTicks, long periodTicks) {
        long initial = Math.max(1L, initialTicks);
        long period = Math.max(1L, periodTicks);
        if (isFolia()) {
            ScheduledTask task = server.getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> runnable.run(), initial, period);
            return task::cancel;
        }
        BukkitTask task = server.getScheduler().runTaskTimer(plugin, runnable, initial, period);
        return task::cancel;
    }

    public TaskHandle runAsync(Runnable runnable) {
        if (isFolia()) {
            ScheduledTask task = server.getAsyncScheduler().runNow(plugin, ignored -> runnable.run());
            return task::cancel;
        }
        BukkitTask task = server.getScheduler().runTaskAsynchronously(plugin, runnable);
        return task::cancel;
    }

    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        return entity.teleportAsync(location);
    }

    public void cancelPlatformTasks() {
        if (isFolia()) {
            server.getGlobalRegionScheduler().cancelTasks(plugin);
            server.getAsyncScheduler().cancelTasks(plugin);
        } else {
            server.getScheduler().cancelTasks(plugin);
        }
    }
}
