package com.zpkdxgames.plexonbackpacks.service;

import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

/**
 * Single capacity contract used by GUI, persistence and storage mutations.
 *
 * <p>The configured tier is the normal authority. Legacy occupied slots beyond
 * that tier remain visible so migration never discards items, but null-only
 * trailing array space is never promoted into usable capacity.</p>
 */
public final class BackpackCapacityInvariant {
    private BackpackCapacityInvariant() {
    }

    public static int authoritativeCapacity(BackpackRecord record, TierDefinition tier) {
        if (record == null || tier == null) {
            throw new IllegalArgumentException("record and tier are required");
        }
        return record.requiredSize(tier.slots());
    }

    public static ItemStack[] normalize(ItemStack[] source, int authoritativeCapacity) {
        requireCapacity(authoritativeCapacity);
        ItemStack[] values = source == null ? new ItemStack[0] : source;
        for (int slot = authoritativeCapacity; slot < values.length; slot++) {
            ItemStack item = values[slot];
            if (item != null && !item.getType().isAir()) {
                throw new IllegalStateException(
                        "Occupied item exists outside authoritative capacity at slot " + slot);
            }
        }
        ItemStack[] normalized = new ItemStack[authoritativeCapacity];
        int copyLength = Math.min(values.length, authoritativeCapacity);
        for (int slot = 0; slot < copyLength; slot++) {
            ItemStack item = values[slot];
            normalized[slot] = item == null ? null : item.clone();
        }
        return normalized;
    }

    public static boolean isNormalized(ItemStack[] source, int authoritativeCapacity) {
        return source != null && source.length == authoritativeCapacity;
    }

    private static void requireCapacity(int capacity) {
        if (capacity < 9 || capacity > 54 || capacity % 9 != 0) {
            throw new IllegalArgumentException("capacity must be a multiple of 9 between 9 and 54");
        }
    }
}
