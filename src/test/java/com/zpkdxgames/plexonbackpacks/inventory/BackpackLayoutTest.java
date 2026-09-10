package com.zpkdxgames.plexonbackpacks.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackpackLayoutTest {
    @Test
    void basicUsesNineStoragePlusControlRow() {
        assertEquals(18, BackpackLayout.inventorySize(9));
        assertEquals(9, BackpackLayout.visibleStorageSlots(9, 0));
    }

    @Test
    void diamondUsesThirtySixStoragePlusControlRow() {
        assertEquals(45, BackpackLayout.inventorySize(36));
        assertEquals(36, BackpackLayout.visibleStorageSlots(36, 0));
    }

    @Test
    void netheriteRetainsAllFiftyFourStorageSlotsAcrossTwoPages() {
        assertEquals(54, BackpackLayout.inventorySize(54));
        assertEquals(2, BackpackLayout.pageCount(54));
        assertEquals(45, BackpackLayout.visibleStorageSlots(54, 0));
        assertEquals(9, BackpackLayout.visibleStorageSlots(54, 1));
    }

    @Test
    void secondPageMapsToRecordSlotFortyFive() {
        assertEquals(45, BackpackLayout.recordSlot(54, 1, 0));
        assertEquals(53, BackpackLayout.recordSlot(54, 1, 8));
    }

    @Test
    void controlRowIsNeverStorage() {
        int control = BackpackLayout.controlRowStart(54);
        assertEquals(45, control);
        assertFalse(BackpackLayout.isStorageGuiSlot(54, 0, control));
    }

    @Test
    void visibleStorageSlotsAreAccepted() {
        assertTrue(BackpackLayout.isStorageGuiSlot(27, 0, 0));
        assertTrue(BackpackLayout.isStorageGuiSlot(27, 0, 26));
    }

    @Test
    void invalidCapacityFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> BackpackLayout.inventorySize(55));
        assertThrows(IllegalArgumentException.class, () -> BackpackLayout.inventorySize(8));
    }

    @Test
    void invalidPageFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> BackpackLayout.visibleStorageSlots(54, 2));
        assertThrows(IllegalArgumentException.class, () -> BackpackLayout.recordSlot(54, 1, 9));
    }
}
