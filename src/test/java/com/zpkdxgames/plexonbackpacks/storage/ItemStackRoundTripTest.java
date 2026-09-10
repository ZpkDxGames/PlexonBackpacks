package com.zpkdxgames.plexonbackpacks.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class ItemStackRoundTripTest {
    private static final NamespacedKey CUSTOM_KEY = new NamespacedKey("plexon-test", "custom_payload");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void roundTripPreservesMaterialAmountAndSimilarity() {
        ItemStack item = customItem();
        ItemStack restored = roundTrip(new ItemStack[]{item})[0];
        assertEquals(item.getType(), restored.getType());
        assertEquals(item.getAmount(), restored.getAmount());
        assertTrue(item.isSimilar(restored));
    }

    @Test
    void roundTripPreservesCustomPdc() {
        ItemStack restored = roundTrip(new ItemStack[]{customItem()})[0];
        assertEquals("plexon-custom-item-v1", restored.getItemMeta().getPersistentDataContainer()
                .get(CUSTOM_KEY, PersistentDataType.STRING));
    }

    @Test
    void roundTripPreservesAdventureNameAndLore() {
        ItemStack source = customItem();
        ItemStack restored = roundTrip(new ItemStack[]{source})[0];
        assertEquals(source.getItemMeta().displayName(), restored.getItemMeta().displayName());
        assertEquals(source.getItemMeta().lore(), restored.getItemMeta().lore());
    }

    @Test
    void roundTripPreservesNullSlots() {
        ItemStack[] restored = roundTrip(new ItemStack[]{customItem(), null, new ItemStack(Material.EMERALD)});
        assertEquals(3, restored.length);
        assertNull(restored[1]);
    }

    private static ItemStack customItem() {
        ItemStack item = new ItemStack(Material.DIAMOND, 7);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Plexon Custom Diamond"));
        meta.lore(List.of(Component.text("component lore"), Component.text("metadata must survive")));
        meta.getPersistentDataContainer().set(CUSTOM_KEY, PersistentDataType.STRING, "plexon-custom-item-v1");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack[] roundTrip(ItemStack[] contents) {
        byte[] bytes = ItemStack.serializeItemsAsBytes(contents);
        return ItemStack.deserializeItemsFromBytes(bytes);
    }
}
