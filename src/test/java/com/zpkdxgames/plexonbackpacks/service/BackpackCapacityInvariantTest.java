package com.zpkdxgames.plexonbackpacks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.RecipeDefinition;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BackpackCapacityInvariantTest {
    private static final TierDefinition BASIC = new TierDefinition(
            "basic", "Basic", "Basic", List.of(), 9, "", null, "", 0.0D,
            new RecipeDefinition(false, List.of(), Map.of()));

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void nullOnlyLegacyTailDoesNotIncreaseAuthoritativeCapacity() {
        ItemStack[] legacy = new ItemStack[54];
        legacy[0] = new ItemStack(Material.DIAMOND);
        BackpackRecord record = new BackpackRecord(UUID.randomUUID(), "basic", null, 1L, 1L, legacy);

        int capacity = BackpackCapacityInvariant.authoritativeCapacity(record, BASIC);
        ItemStack[] normalized = BackpackCapacityInvariant.normalize(record.contents(), capacity);

        assertEquals(9, capacity);
        assertEquals(9, normalized.length);
        assertEquals(Material.DIAMOND, normalized[0].getType());
    }

    @Test
    void occupiedLegacyOverflowIsPreservedAndMadeVisible() {
        ItemStack[] legacy = new ItemStack[54];
        legacy[17] = new ItemStack(Material.NETHERITE_INGOT);
        BackpackRecord record = new BackpackRecord(UUID.randomUUID(), "basic", null, 1L, 1L, legacy);

        int capacity = BackpackCapacityInvariant.authoritativeCapacity(record, BASIC);
        ItemStack[] normalized = BackpackCapacityInvariant.normalize(record.contents(), capacity);

        assertEquals(18, capacity);
        assertEquals(18, normalized.length);
        assertEquals(Material.NETHERITE_INGOT, normalized[17].getType());
    }

    @Test
    void explicitCapacityCannotDiscardOccupiedItems() {
        ItemStack[] source = new ItemStack[18];
        source[9] = new ItemStack(Material.EMERALD);
        assertThrows(IllegalStateException.class, () -> BackpackCapacityInvariant.normalize(source, 9));
    }

    @Test
    void normalizationClonesExactItems() {
        ItemStack item = new ItemStack(Material.DIAMOND, 7);
        ItemStack[] normalized = BackpackCapacityInvariant.normalize(new ItemStack[]{item}, 9);
        assertEquals(item, normalized[0]);
        assertNotSame(item, normalized[0]);
    }
}
