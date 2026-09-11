package com.zpkdxgames.plexonbackpacks.inventory;

import java.util.Arrays;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class BackpackHolder implements InventoryHolder {
    private final UUID backpackId;
    private final UUID viewerId;
    private final String tierId;
    private final UUID sessionId;
    private final int capacity;
    private Inventory inventory;
    private int page;
    private int snapshotHash;

    public BackpackHolder(
            UUID backpackId,
            UUID viewerId,
            String tierId,
            UUID sessionId,
            int capacity
    ) {
        this.backpackId = backpackId;
        this.viewerId = viewerId;
        this.tierId = tierId;
        this.sessionId = sessionId;
        this.capacity = capacity;
        this.page = 0;
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

    public int capacity() {
        return capacity;
    }

    public int page() {
        return page;
    }

    public int pageCount() {
        return BackpackLayout.pageCount(capacity);
    }

    public int visibleStorageSlots() {
        return BackpackLayout.visibleStorageSlots(capacity, page);
    }

    public int controlRowStart() {
        return BackpackLayout.controlRowStart(capacity);
    }

    public void page(int page) {
        if (page < 0 || page >= pageCount()) {
            throw new IllegalArgumentException("page outside backpack capacity");
        }
        this.page = page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public ItemStack[] visibleStorageContents() {
        ItemStack[] result = new ItemStack[visibleStorageSlots()];
        for (int slot = 0; slot < result.length; slot++) {
            ItemStack item = getInventory().getItem(slot);
            result[slot] = item == null ? null : item.clone();
        }
        return result;
    }

    public void resetSnapshotHash() {
        snapshotHash = Arrays.hashCode(visibleStorageContents());
    }

    public boolean contentsChanged() {
        int currentHash = Arrays.hashCode(visibleStorageContents());
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
