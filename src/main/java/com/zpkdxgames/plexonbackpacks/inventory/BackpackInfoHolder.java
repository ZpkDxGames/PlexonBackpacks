package com.zpkdxgames.plexonbackpacks.inventory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Typed presentation-only holder for Backpack Info / Upgrade. */
public final class BackpackInfoHolder implements InventoryHolder {
    private final UUID backpackId;
    private final String expectedTierId;
    private final int returnPage;
    private final AtomicBoolean submitted = new AtomicBoolean();
    private Inventory inventory;

    public BackpackInfoHolder(UUID backpackId, String expectedTierId, int returnPage) {
        this.backpackId = backpackId;
        this.expectedTierId = expectedTierId;
        this.returnPage = Math.max(0, returnPage);
    }

    public UUID backpackId() {
        return backpackId;
    }

    public String expectedTierId() {
        return expectedTierId;
    }

    public int returnPage() {
        return returnPage;
    }

    public boolean trySubmit() {
        return submitted.compareAndSet(false, true);
    }

    public void attach(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Backpack Info inventory already attached");
        }
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Backpack Info inventory not attached");
        }
        return inventory;
    }
}
