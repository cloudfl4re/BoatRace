package cn.cloudfl4re.boatrace.listener;

import cn.cloudfl4re.boatrace.service.EditorManager;
import cn.cloudfl4re.boatrace.service.RaceManager;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public final class RaceListener implements Listener {
    private final RaceManager races;
    private final EditorManager editors;

    public RaceListener(RaceManager races, EditorManager editors) {
        this.races = races;
        this.editors = editors;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat) || boat.getPassengers().isEmpty()) {
            return;
        }
        Entity driver = boat.getPassengers().getFirst();
        if (driver instanceof Player player) {
            races.handleVehicleMove(boat, player, event.getFrom(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (races.isSpectator(event.getPlayer().getUniqueId())
            && races.shouldBlockSpectatorMove(event.getPlayer().getUniqueId(), event.getTo())) {
            event.setCancelled(true);
        } else if (races.isStaged(event.getPlayer().getUniqueId()) && !event.getPlayer().isInsideVehicle()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && races.isProtectedPlayer(player.getUniqueId())) {
            event.setCancelled(true);
        } else if (races.isRaceBoat(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (races.isRaceBoat(event.getVehicle().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (races.isRaceBoat(event.getVehicle().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleCollision(VehicleEntityCollisionEvent event) {
        if (races.shouldCancelRaceBoatCollision(event.getVehicle().getUniqueId(), event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            if (races.shouldBlockVehicleExit(player, event.getVehicle()) && event.isCancellable()) {
                event.setCancelled(true);
                races.notifyExitBlocked(player);
            } else {
                races.handleVehicleExit(player, event.getVehicle());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        editors.cancel(event.getPlayer().getUniqueId());
        races.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        races.handleJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        races.handleDeath(event.getPlayer().getUniqueId());
    }
}
