package com.zpkdxgames.plexonbackpacks.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackpackViewModelTest {
    @Test
    void reportsTierOwnerCapacityAndEmptyState() {
        BackpackViewModel view = new BackpackViewModel(
                "Gold Backpack", "Tonim", 27, 0, 0, 1, false, true);

        assertEquals("Gold Backpack", view.tierName());
        assertEquals("Tonim", view.ownerName());
        assertEquals(27, view.capacity());
        assertEquals(27, view.freeSlots());
        assertEquals(BackpackViewModel.StorageState.EMPTY, view.storageState());
        assertTrue(view.quickDepositEnabled());
        assertFalse(view.adminOverride());
    }

    @Test
    void reportsUsedFreePageAndFullState() {
        BackpackViewModel view = new BackpackViewModel(
                "Netherite Backpack", "Owner", 54, 54, 1, 2, true, true);

        assertEquals(0, view.freeSlots());
        assertEquals(1, view.page());
        assertEquals(2, view.pageCount());
        assertEquals(BackpackViewModel.StorageState.FULL, view.storageState());
        assertTrue(view.adminOverride());
    }

    @Test
    void partialStorageRemainsAvailable() {
        BackpackViewModel view = new BackpackViewModel(
                "Diamond Backpack", "Owner", 36, 17, 0, 1, false, false);

        assertEquals(19, view.freeSlots());
        assertEquals(BackpackViewModel.StorageState.AVAILABLE, view.storageState());
        assertFalse(view.quickDepositEnabled());
    }
}
