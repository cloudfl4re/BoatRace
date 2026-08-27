package cn.cloudfl4re.boatrace.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class GuiListener implements Listener {
    private final GuiService service;

    public GuiListener(GuiService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        if (!holder.layoutEditor() || !topSlot) {
            event.setCancelled(true);
        }
        if (topSlot && event.getWhoClicked() instanceof Player player && !holder.layoutEditor()) {
            service.click(player, holder, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (!holder.layoutEditor() || event.getRawSlots().stream()
            .anyMatch(slot -> slot < 0 || slot >= event.getInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            if (holder.layoutEditor()) {
                service.saveLayoutAsync(player, holder, event.getInventory());
            } else {
                service.onClose(player.getUniqueId(), holder.menu());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!service.hasPrompt(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        service.acceptChat(event.getPlayer().getUniqueId(), event.getMessage());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.cleanup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        service.cleanup(event.getPlayer().getUniqueId());
    }
}
