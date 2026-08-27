package cn.cloudfl4re.boatrace.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class GuiListener implements Listener {
    private final GuiService service;

    public GuiListener(GuiService service) { this.service = service; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(!holder.layoutEditor());
        if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()) {
            service.click(player, holder, event.getRawSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder) event.setCancelled(!holder.layoutEditor());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder) || !holder.layoutEditor()) return;
        if (event.getPlayer() instanceof Player player) {
            try {
                service.layoutEditor().save(player, holder, event.getInventory());
            } catch (Exception exception) {
                player.sendMessage("§c菜单布局保存失败，已保留原配置。");
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!service.hasPrompt(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        service.acceptChat(event.getPlayer(), event.getMessage());
    }
}
