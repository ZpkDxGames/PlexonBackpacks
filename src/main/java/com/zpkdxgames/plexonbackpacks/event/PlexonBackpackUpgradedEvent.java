package com.zpkdxgames.plexonbackpacks.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PlexonBackpackUpgradedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final UUID backpackId;
    private final String previousTier;
    private final String newTier;
    private final double cost;

    public PlexonBackpackUpgradedEvent(
            Player player,
            UUID backpackId,
            String previousTier,
            String newTier,
            double cost
    ) {
        this.player = player;
        this.backpackId = backpackId;
        this.previousTier = previousTier;
        this.newTier = newTier;
        this.cost = cost;
    }

    public Player player() {
        return player;
    }

    public UUID backpackId() {
        return backpackId;
    }

    public String previousTier() {
        return previousTier;
    }

    public String newTier() {
        return newTier;
    }

    public double cost() {
        return cost;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
