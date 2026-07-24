package com.zpkdxgames.plexonbackpacks.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class BackpackHolder implements InventoryHolder {
    private final UUID backpackId;
    private final UUID viewerId;
    private final String tierId;
    private Inventory inventory;

    public BackpackHolder(UUID backpackId, UUID viewerId, String tierId) {
        this.backpackId = backpackId;
        this.viewerId = viewerId;
        this.tierId = tierId;
    }

    public UUID backpackId() {
        return backpackId;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public String tierId() {
        return tierId;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Backpack inventory has not been initialized");
        }
        return inventory;
    }
}
