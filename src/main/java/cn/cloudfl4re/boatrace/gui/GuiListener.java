package cn.cloudfl4re.boatrace.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {
    private final GuiService service;

    public GuiListener(GuiService service) { this.service = service; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()) {
            service.click(player, holder, event.getRawSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) event.setCancelled(true);
    }
}
