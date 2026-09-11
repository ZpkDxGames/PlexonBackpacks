package com.zpkdxgames.plexonbackpacks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {
    @Test
    void reservesOneAuthoritativeSession() {
        SessionRegistry registry = new SessionRegistry();
        UUID player = UUID.randomUUID();
        UUID backpack = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        assertEquals(SessionRegistry.ReservationResult.RESERVED, registry.reserve(player, backpack, session));
        assertEquals(session, registry.byPlayer(player).orElseThrow().sessionId());
        assertEquals(session, registry.byBackpack(backpack).orElseThrow().sessionId());
    }

    @Test
    void rejectsSamePlayerDoubleOpen() {
        SessionRegistry registry = new SessionRegistry();
        UUID player = UUID.randomUUID();
        assertEquals(SessionRegistry.ReservationResult.RESERVED,
                registry.reserve(player, UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(SessionRegistry.ReservationResult.PLAYER_BUSY,
                registry.reserve(player, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void rejectsTwoPlayersForSameBackpack() {
        SessionRegistry registry = new SessionRegistry();
        UUID backpack = UUID.randomUUID();
        assertEquals(SessionRegistry.ReservationResult.RESERVED,
                registry.reserve(UUID.randomUUID(), backpack, UUID.randomUUID()));
        assertEquals(SessionRegistry.ReservationResult.BACKPACK_BUSY,
                registry.reserve(UUID.randomUUID(), backpack, UUID.randomUUID()));
    }

    @Test
    void staleCloseCannotReleaseNewerSession() {
        SessionRegistry registry = new SessionRegistry();
        UUID player = UUID.randomUUID();
        UUID backpack = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        registry.reserve(player, backpack, active);
        assertTrue(registry.releaseByPlayer(player, UUID.randomUUID()).isEmpty());
        assertTrue(registry.byBackpack(backpack).isPresent());
    }

    @Test
    void reconnectCanReserveAfterOldSessionReleased() {
        SessionRegistry registry = new SessionRegistry();
        UUID player = UUID.randomUUID();
        UUID backpack = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        registry.reserve(player, backpack, first);
        assertTrue(registry.releaseByPlayer(player, first).isPresent());
        assertEquals(SessionRegistry.ReservationResult.RESERVED,
                registry.reserve(player, backpack, UUID.randomUUID()));
    }

    @Test
    void forceReleaseClearsBothIndexes() {
        SessionRegistry registry = new SessionRegistry();
        UUID player = UUID.randomUUID();
        UUID backpack = UUID.randomUUID();
        registry.reserve(player, backpack, UUID.randomUUID());
        assertTrue(registry.forceReleaseBackpack(backpack).isPresent());
        assertTrue(registry.byPlayer(player).isEmpty());
        assertTrue(registry.byBackpack(backpack).isEmpty());
    }

    @Test
    void sessionAndLockCountsStayInSync() {
        SessionRegistry registry = new SessionRegistry();
        registry.reserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        registry.reserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertEquals(2, registry.sessionCount());
        assertEquals(2, registry.lockCount());
    }

    @Test
    void clearRemovesAllAuthority() {
        SessionRegistry registry = new SessionRegistry();
        registry.reserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        registry.clear();
        assertEquals(0, registry.sessionCount());
        assertEquals(0, registry.lockCount());
        assertFalse(registry.forceReleaseBackpack(UUID.randomUUID()).isPresent());
    }
}
