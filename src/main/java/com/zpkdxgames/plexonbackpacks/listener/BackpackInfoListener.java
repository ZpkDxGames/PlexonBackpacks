package com.zpkdxgames.plexonbackpacks.listener;

import com.zpkdxgames.plexonbackpacks.inventory.BackpackInfoGui;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackInfoHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Protects the presentation-only Backpack Info inventory and routes its explicit actions. */
public final class BackpackInfoListener implements Listener {
    private final BackpackInfoGui gui;

    public BackpackInfoListener(BackpackInfoGui gui) {
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackInfoHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        gui.handleClick(player, holder, rawSlot);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BackpackInfoHolder) {
            event.setCancelled(true);
        }
    }
}
