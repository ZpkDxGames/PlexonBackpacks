package com.zpkdxgames.plexonbackpacks.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Main-thread registry enforcing one authoritative viewer per backpack and one
 * authoritative backpack session per player.
 */
public final class SessionRegistry {
    public enum ReservationResult {
        RESERVED,
        PLAYER_BUSY,
        BACKPACK_BUSY
    }

    public record Session(UUID sessionId, UUID playerId, UUID backpackId) {
        public Session {
            if (sessionId == null || playerId == null || backpackId == null) {
                throw new IllegalArgumentException("sessionId, playerId and backpackId are required");
            }
        }
    }

    private final Map<UUID, Session> byPlayer = new HashMap<>();
    private final Map<UUID, Session> byBackpack = new HashMap<>();

    public ReservationResult reserve(UUID playerId, UUID backpackId, UUID sessionId) {
        if (byPlayer.containsKey(playerId)) {
            return ReservationResult.PLAYER_BUSY;
        }
        if (byBackpack.containsKey(backpackId)) {
            return ReservationResult.BACKPACK_BUSY;
        }
        Session session = new Session(sessionId, playerId, backpackId);
        byPlayer.put(playerId, session);
        byBackpack.put(backpackId, session);
        return ReservationResult.RESERVED;
    }

    public Optional<Session> byPlayer(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    public Optional<Session> byBackpack(UUID backpackId) {
        return Optional.ofNullable(byBackpack.get(backpackId));
    }

    public Optional<Session> releaseByPlayer(UUID playerId, UUID expectedSessionId) {
        Session session = byPlayer.get(playerId);
        if (session == null || !session.sessionId().equals(expectedSessionId)) {
            return Optional.empty();
        }
        byPlayer.remove(playerId);
        byBackpack.remove(session.backpackId(), session);
        return Optional.of(session);
    }

    public Optional<Session> forceReleaseBackpack(UUID backpackId) {
        Session session = byBackpack.remove(backpackId);
        if (session == null) {
            return Optional.empty();
        }
        byPlayer.remove(session.playerId(), session);
        return Optional.of(session);
    }

    public int sessionCount() {
        return byPlayer.size();
    }

    public int lockCount() {
        return byBackpack.size();
    }

    public void clear() {
        byPlayer.clear();
        byBackpack.clear();
    }
}
