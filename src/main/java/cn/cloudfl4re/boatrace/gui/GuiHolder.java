package cn.cloudfl4re.boatrace.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class GuiHolder implements InventoryHolder {
    private final String menu;
    private final UUID playerId;
    private final boolean layoutEditor;
    private Inventory inventory;

    public GuiHolder(String menu, UUID playerId, boolean layoutEditor) {
        this.menu = menu;
        this.playerId = playerId;
        this.layoutEditor = layoutEditor;
    }

    public String menu() { return menu; }
    public UUID playerId() { return playerId; }
    public boolean layoutEditor() { return layoutEditor; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}


