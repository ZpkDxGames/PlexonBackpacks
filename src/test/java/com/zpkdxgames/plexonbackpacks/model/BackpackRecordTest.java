package com.zpkdxgames.plexonbackpacks.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BackpackRecordTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void constructorClonesItemStacks() {
        ItemStack original = new ItemStack(Material.DIAMOND, 3);
        BackpackRecord record = new BackpackRecord(
                UUID.randomUUID(), "basic", null, 1L, 1L, new ItemStack[]{original});
        ItemStack stored = record.contents()[0];
        assertNotSame(original, stored);
        assertEquals(3, stored.getAmount());
        original.setAmount(1);
        assertEquals(3, record.contents()[0].getAmount());
    }

    @Test
    void contentsGetterReturnsDefensiveCopies() {
        BackpackRecord record = new BackpackRecord(
                UUID.randomUUID(), "basic", null, 1L, 1L,
                new ItemStack[]{new ItemStack(Material.EMERALD, 4)});
        ItemStack[] first = record.contents();
        first[0].setAmount(1);
        assertEquals(4, record.contents()[0].getAmount());
    }

    @Test
    void requiredSizeNeverShrinksOccupiedLegacyStorage() {
        ItemStack[] contents = new ItemStack[36];
        contents[35] = new ItemStack(Material.DIAMOND);
        BackpackRecord record = new BackpackRecord(
                UUID.randomUUID(), "basic", null, 1L, 1L, contents);
        assertEquals(36, record.requiredSize(9));
    }

    @Test
    void nullSlotsStayNullAcrossCopies() {
        BackpackRecord record = new BackpackRecord(
                UUID.randomUUID(), "basic", null, 1L, 1L, new ItemStack[9]);
        assertNull(record.contents()[4]);
    }
}
