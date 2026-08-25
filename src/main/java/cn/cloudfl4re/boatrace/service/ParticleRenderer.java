package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.config.PluginSettings;
import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.StartSlot;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class ParticleRenderer {
    private final Supplier<PluginSettings> settings;

    public ParticleRenderer(Supplier<PluginSettings> settings) {
        this.settings = settings;
    }

    public void gate(Player player, Cuboid gate, Color color) {
        PluginSettings current = settings.get();
        if (player.getLocation().toVector().distanceSquared(new org.bukkit.util.Vector(gate.center().x(), gate.center().y(), gate.center().z())) > current.particleViewDistance() * current.particleViewDistance()) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        double spacing = current.particleSpacing();
        lineX(player, gate.minX(), gate.maxX(), gate.minY(), gate.minZ(), spacing, dust);
        lineX(player, gate.minX(), gate.maxX(), gate.minY(), gate.maxZ(), spacing, dust);
        lineX(player, gate.minX(), gate.maxX(), gate.maxY(), gate.minZ(), spacing, dust);
        lineX(player, gate.minX(), gate.maxX(), gate.maxY(), gate.maxZ(), spacing, dust);
        lineY(player, gate.minY(), gate.maxY(), gate.minX(), gate.minZ(), spacing, dust);
        lineY(player, gate.minY(), gate.maxY(), gate.minX(), gate.maxZ(), spacing, dust);
        lineY(player, gate.minY(), gate.maxY(), gate.maxX(), gate.minZ(), spacing, dust);
        lineY(player, gate.minY(), gate.maxY(), gate.maxX(), gate.maxZ(), spacing, dust);
        lineZ(player, gate.minZ(), gate.maxZ(), gate.minX(), gate.minY(), spacing, dust);
        lineZ(player, gate.minZ(), gate.maxZ(), gate.minX(), gate.maxY(), spacing, dust);
        lineZ(player, gate.minZ(), gate.maxZ(), gate.maxX(), gate.minY(), spacing, dust);
        lineZ(player, gate.minZ(), gate.maxZ(), gate.maxX(), gate.maxY(), spacing, dust);
    }

    public void slot(Player player, StartSlot slot) {
        double distance = player.getLocation().toVector().distanceSquared(new org.bukkit.util.Vector(slot.x(), slot.y(), slot.z()));
        if (distance > settings.get().particleViewDistance() * settings.get().particleViewDistance()) {
            return;
        }
        for (int index = 0; index <= 4; index++) {
            player.spawnParticle(Particle.END_ROD, slot.x(), slot.y() + index * 0.5, slot.z(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void lineX(Player player, double min, double max, double y, double z, double spacing, Particle.DustOptions dust) {
        sample(min, max, spacing, x -> particle(player, x, y, z, dust));
    }

    private static void lineY(Player player, double min, double max, double x, double z, double spacing, Particle.DustOptions dust) {
        sample(min, max, spacing, y -> particle(player, x, y, z, dust));
    }

    private static void lineZ(Player player, double min, double max, double x, double y, double spacing, Particle.DustOptions dust) {
        sample(min, max, spacing, z -> particle(player, x, y, z, dust));
    }

    private static void sample(double min, double max, double spacing, java.util.function.DoubleConsumer consumer) {
        double length = Math.max(0.0, max - min);
        int count = Math.min(64, Math.max(1, (int) Math.ceil(length / spacing)));
        for (int index = 0; index <= count; index++) {
            consumer.accept(min + length * index / count);
        }
    }

    private static void particle(Player player, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust);
    }
}
