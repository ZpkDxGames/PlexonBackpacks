package com.zpkdxgames.plexonbackpacks.api;

import java.util.UUID;

/** Immutable snapshot of a currently open backpack session. */
public record BackpackSessionView(
        UUID playerId,
        UUID backpackId,
        String tierId,
        UUID sessionId
) {
}
