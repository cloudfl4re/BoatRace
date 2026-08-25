package cn.cloudfl4re.boatrace.harness;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class BotHarnessPlugin extends JavaPlugin implements CommandExecutor {
    @Override
    public void onEnable() {
        var command = getCommand("brtest");
        if (command == null) {
            throw new IllegalStateException("brtest command is missing");
        }
        command.setExecutor(this);
        var location = getCommand("brloc");
        if (location == null) {
            throw new IllegalStateException("brloc command is missing");
        }
        location.setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("brloc")) {
            if (args.length < 1) {
                sender.sendPlainMessage("Usage: /brloc <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendPlainMessage("Player not found");
                return true;
            }
            target.getScheduler().run(this, task -> {
                var vehicle = target.getVehicle();
                if (vehicle == null) {
                    getLogger().info("Vehicle state " + target.getName() + ": NONE");
                } else {
                    var position = vehicle.getLocation();
                    getLogger().info("Vehicle state " + target.getName() + ": " + vehicle.getType() + " @ " + position.getX() + "," + position.getY() + "," + position.getZ());
                }
            }, null);
            return true;
        }
        if (args.length < 2) {
            sender.sendPlainMessage("Usage: /brtest <player> <command>");
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[0]);
        if (player == null) {
            sender.sendPlainMessage("Player not found");
            return true;
        }
        String delegated = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        player.getScheduler().run(this, task -> getLogger().info("Player command result: " + player.performCommand(delegated)), null);
        sender.sendPlainMessage("Dispatched as " + player.getName() + ": " + delegated);
        return true;
    }
}
