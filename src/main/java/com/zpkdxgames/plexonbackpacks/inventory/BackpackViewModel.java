package com.zpkdxgames.plexonbackpacks.inventory;

import java.util.Objects;

/** Compact immutable presentation state for the storage control strip. */
public record BackpackViewModel(
        String tierName,
        String ownerName,
        int capacity,
        int usedSlots,
        int page,
        int pageCount,
        boolean adminOverride,
        boolean quickDepositEnabled
) {
    public BackpackViewModel {
        tierName = Objects.requireNonNullElse(tierName, "Unknown");
        ownerName = Objects.requireNonNullElse(ownerName, "Unbound");
        if (capacity < 0 || usedSlots < 0 || usedSlots > capacity) {
            throw new IllegalArgumentException("used slots must be within capacity");
        }
        if (pageCount < 1 || page < 0 || page >= pageCount) {
            throw new IllegalArgumentException("page must be within page count");
        }
    }

    public int freeSlots() {
        return capacity - usedSlots;
    }

    public StorageState storageState() {
        if (usedSlots == 0) {
            return StorageState.EMPTY;
        }
        if (usedSlots == capacity) {
            return StorageState.FULL;
        }
        return StorageState.AVAILABLE;
    }

    public enum StorageState {
        EMPTY,
        AVAILABLE,
        FULL
    }
}
