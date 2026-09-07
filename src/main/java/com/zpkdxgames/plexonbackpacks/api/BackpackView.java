package com.zpkdxgames.plexonbackpacks.api;

import java.util.UUID;

/** Immutable metadata snapshot of one persistent backpack. */
public record BackpackView(
        UUID backpackId,
        String tierId,
        UUID ownerId,
        long createdAtMs,
        long lastAccessMs,
        int size
) {
}
