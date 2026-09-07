package com.zpkdxgames.plexonbackpacks.api;

/** Immutable public view of a configured backpack tier. */
public record TierView(
        String id,
        int slots,
        String permission,
        Integer customModelData
) {
}
