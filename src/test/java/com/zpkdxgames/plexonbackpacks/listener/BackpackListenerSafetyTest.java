package com.zpkdxgames.plexonbackpacks.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.item.BackpackNestingPolicy;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BackpackListenerSafetyTest {
    private BackpackItemFactory itemFactory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        PlexonBackpacksPlugin plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        ConfigManager config = new ConfigManager(plugin);
        config.reload();
        itemFactory = new BackpackItemFactory(plugin, config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void directLegacyBackpackCanBePickedUpOrShiftExtractedWithEmptyCursor() {
        ItemStack nested = itemFactory.create(
                new ConfigManagerTier(itemFactory).basic(), null);
        ItemStack empty = new ItemStack(Material.AIR);

        assertTrue(BackpackListener.legacyNestedExtractionAllowed(
                InventoryAction.PICKUP_ALL, nested, empty, itemFactory));
        assertTrue(BackpackListener.legacyNestedExtractionAllowed(
                InventoryAction.MOVE_TO_OTHER_INVENTORY, nested, empty, itemFactory));
        assertFalse(BackpackListener.legacyNestedExtractionAllowed(
                InventoryAction.SWAP_WITH_CURSOR, nested, new ItemStack(Material.STONE), itemFactory));
    }

    @Test
    void containerMediatedLegacyBackpackCanBeExtractedButStillMatchesNestingGuard() {
        ItemStack nested = itemFactory.create(
                new ConfigManagerTier(itemFactory).basic(), null);
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) shulker.getItemMeta();
        Container container = (Container) meta.getBlockState();
        container.getInventory().setItem(0, nested);
        meta.setBlockState(container);
        shulker.setItemMeta(meta);

        assertTrue(BackpackNestingPolicy.containsBackpack(shulker, itemFactory));
        assertTrue(BackpackListener.legacyNestedExtractionAllowed(
                InventoryAction.PICKUP_ALL, shulker, new ItemStack(Material.AIR), itemFactory));
        assertFalse(BackpackListener.legacyNestedExtractionAllowed(
                InventoryAction.PLACE_ALL, shulker, new ItemStack(Material.STONE), itemFactory));
    }

    /** Keeps the test focused on listener policy without exposing ConfigManager from the factory. */
    private static final class ConfigManagerTier {
        private final BackpackItemFactory ignored;

        private ConfigManagerTier(BackpackItemFactory ignored) {
            this.ignored = ignored;
        }

        private com.zpkdxgames.plexonbackpacks.model.TierDefinition basic() {
            return new com.zpkdxgames.plexonbackpacks.model.TierDefinition(
                    "basic", "Basic", "Basic", java.util.List.of(), 9, "", null, "", 0.0D,
                    new com.zpkdxgames.plexonbackpacks.model.RecipeDefinition(
                            false, java.util.List.of(), java.util.Map.of()));
        }
    }
}
