package com.zpkdxgames.plexonbackpacks.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class AdminMenuHolder implements InventoryHolder {
    public static final int AUTHOR_SLOT = 22;
    public static final int CLOSE_SLOT = 26;

    private final Map<Integer, String> tiersBySlot = new HashMap<>();
    private Inventory inventory;

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void bindTier(int slot, String tierId) {
        tiersBySlot.put(slot, tierId);
    }

    public String tierAt(int slot) {
        return tiersBySlot.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Admin menu inventory has not been initialized");
        }
        return inventory;
    }
}
