package cn.cloudfl4re.boatrace.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;
import java.util.UUID;

public record StoredLocation(UUID worldId, double x, double y, double z, float yaw, float pitch) {
    public static StoredLocation from(Location location) {
        return new StoredLocation(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Optional<Location> resolve() {
        World world = Bukkit.getWorld(worldId);
        return world == null ? Optional.empty() : Optional.of(new Location(world, x, y, z, yaw, pitch));
    }
}
