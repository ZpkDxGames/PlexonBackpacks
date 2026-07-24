package com.zpkdxgames.plexonbackpacks.model;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.UUID;

public final class BackpackRecord {
    private final UUID id;
    private final long createdAt;
    private String tierId;
    private UUID owner;
    private long lastAccess;
    private ItemStack[] contents;

    public BackpackRecord(
            UUID id,
            String tierId,
            UUID owner,
            long createdAt,
            long lastAccess,
            ItemStack[] contents
    ) {
        this.id = id;
        this.tierId = tierId;
        this.owner = owner;
        this.createdAt = createdAt;
        this.lastAccess = lastAccess;
        this.contents = cloneContents(contents);
    }

    public UUID id() {
        return id;
    }

    public String tierId() {
        return tierId;
    }

    public void tierId(String tierId) {
        this.tierId = tierId;
    }

    public UUID owner() {
        return owner;
    }

    public void owner(UUID owner) {
        this.owner = owner;
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastAccess() {
        return lastAccess;
    }

    public void lastAccess(long lastAccess) {
        this.lastAccess = lastAccess;
    }

    public ItemStack[] contents() {
        return cloneContents(contents);
    }

    public void contents(ItemStack[] contents) {
        this.contents = cloneContents(contents);
    }

    public int requiredSize(int configuredSize) {
        int lastOccupied = -1;
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            if (item != null && !item.getType().isAir()) {
                lastOccupied = index;
            }
        }
        int occupiedSize = lastOccupied < 0 ? 0 : ((lastOccupied / 9) + 1) * 9;
        return Math.max(configuredSize, Math.min(54, occupiedSize));
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        return Arrays.stream(source)
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }
}
