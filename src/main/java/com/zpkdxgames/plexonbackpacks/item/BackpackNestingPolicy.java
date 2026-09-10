package com.zpkdxgames.plexonbackpacks.item;

import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * PDC-based anti-nesting policy. It also inspects Bukkit container block-state
 * metadata (for example shulker boxes) to prevent common indirect nesting.
 */
public final class BackpackNestingPolicy {
    private static final int MAX_CONTAINER_DEPTH = 4;

    private BackpackNestingPolicy() {
    }

    public static boolean containsBackpack(ItemStack item, BackpackItemFactory itemFactory) {
        return containsBackpack(item, itemFactory, 0);
    }

    private static boolean containsBackpack(ItemStack item, BackpackItemFactory itemFactory, int depth) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (itemFactory.isBackpack(item)) {
            return true;
        }
        if (depth >= MAX_CONTAINER_DEPTH || !(item.getItemMeta() instanceof BlockStateMeta stateMeta)) {
            return false;
        }
        if (!(stateMeta.getBlockState() instanceof Container container)) {
            return false;
        }
        for (ItemStack nested : container.getInventory().getContents()) {
            if (containsBackpack(nested, itemFactory, depth + 1)) {
                return true;
            }
        }
        return false;
    }
}
