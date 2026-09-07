package com.zpkdxgames.plexonbackpacks.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PlexonBackpackBoundEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID backpackId;
    private final String tierId;
    private final UUID ownerId;
    private final String eventId;
    private final UUID sessionId;

    public PlexonBackpackBoundEvent(
            Player player,
            UUID backpackId,
            String tierId,
            UUID ownerId,
            String eventId,
            UUID sessionId
    ) {
        this.player = player;
        this.backpackId = backpackId;
        this.tierId = tierId;
        this.ownerId = ownerId;
        this.eventId = eventId;
        this.sessionId = sessionId;
    }

    public Player getPlayer() { return player; }
    public UUID getBackpackId() { return backpackId; }
    public String getTierId() { return tierId; }
    public UUID getOwnerId() { return ownerId; }
    public String getEventId() { return eventId; }
    public UUID getSessionId() { return sessionId; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
