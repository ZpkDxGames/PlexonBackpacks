package com.zpkdxgames.plexonbackpacks.inventory;

/**
 * Deterministic storage-to-GUI mapping. The final row is always reserved for
 * controls; all preceding slots are storage slots for the active page.
 */
public final class BackpackLayout {
    public static final int CONTROL_ROW_SIZE = 9;
    public static final int MAX_STORAGE_PER_PAGE = 45;

    public static final int INFO_SLOT_OFFSET = 0;
    public static final int PREVIOUS_SLOT_OFFSET = 2;
    public static final int SORT_SLOT_OFFSET = 3;
    public static final int QUICK_DEPOSIT_SLOT_OFFSET = 4;
    public static final int CLOSE_SLOT_OFFSET = 5;
    public static final int NEXT_SLOT_OFFSET = 6;

    private BackpackLayout() {
    }

    public static int inventorySize(int capacity) {
        requireCapacity(capacity);
        int visible = Math.min(capacity, MAX_STORAGE_PER_PAGE);
        return visible + CONTROL_ROW_SIZE;
    }

    public static int pageSize(int capacity) {
        requireCapacity(capacity);
        return Math.min(capacity, MAX_STORAGE_PER_PAGE);
    }

    public static int pageCount(int capacity) {
        requireCapacity(capacity);
        return (capacity + MAX_STORAGE_PER_PAGE - 1) / MAX_STORAGE_PER_PAGE;
    }

    public static int pageStart(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page cannot be negative");
        }
        return page * MAX_STORAGE_PER_PAGE;
    }

    public static int visibleStorageSlots(int capacity, int page) {
        int pages = pageCount(capacity);
        if (page < 0 || page >= pages) {
            throw new IllegalArgumentException("page outside backpack capacity");
        }
        return Math.min(MAX_STORAGE_PER_PAGE, capacity - pageStart(page));
    }

    public static int controlRowStart(int capacity) {
        return inventorySize(capacity) - CONTROL_ROW_SIZE;
    }

    public static boolean isStorageGuiSlot(int capacity, int page, int guiSlot) {
        return guiSlot >= 0 && guiSlot < visibleStorageSlots(capacity, page);
    }

    public static int recordSlot(int capacity, int page, int guiSlot) {
        if (!isStorageGuiSlot(capacity, page, guiSlot)) {
            throw new IllegalArgumentException("GUI slot is not a visible storage slot");
        }
        return pageStart(page) + guiSlot;
    }

    private static void requireCapacity(int capacity) {
        if (capacity < 9 || capacity > 54 || capacity % 9 != 0) {
            throw new IllegalArgumentException("capacity must be a multiple of 9 between 9 and 54");
        }
    }
}
