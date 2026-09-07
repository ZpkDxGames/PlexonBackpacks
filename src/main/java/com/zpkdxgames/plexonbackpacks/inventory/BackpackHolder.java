package com.zpkdxgames.plexonbackpacks.inventory;

import java.util.Arrays;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class BackpackHolder implements InventoryHolder {
    private final UUID backpackId;
    private final UUID viewerId;
    private final String tierId;
    private final UUID sessionId;
    private Inventory inventory;
    private int snapshotHash;

    public BackpackHolder(UUID backpackId, UUID viewerId, String tierId, UUID sessionId) {
        this.backpackId = backpackId;
        this.viewerId = viewerId;
        this.tierId = tierId;
        this.sessionId = sessionId;
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

    public UUID sessionId() {
        return sessionId;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void resetSnapshotHash() {
        snapshotHash = Arrays.hashCode(getInventory().getStorageContents());
    }

    public boolean contentsChanged() {
        int currentHash = Arrays.hashCode(getInventory().getStorageContents());
        if (currentHash == snapshotHash) {
            return false;
        }
        snapshotHash = currentHash;
        return true;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Backpack inventory has not been initialized");
        }
        return inventory;
    }
}
