package com.zpkdxgames.plexonbackpacks.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Stable public service API for PlexonBackpacks.
 *
 * <p>Inventory mutations ({@link #create(String)} and {@link #open(Player, ItemStack)})
 * are primary-thread operations. Metadata views are immutable snapshots and never
 * expose the live storage cache.</p>
 */
public interface PlexonBackpacksAPI {
    boolean isBackpack(ItemStack item);
    Optional<UUID> backpackId(ItemStack item);
    Optional<String> tierId(ItemStack item);
    Optional<UUID> owner(ItemStack item);
    Optional<BackpackView> backpack(UUID backpackId);
    Collection<TierView> tiers();
    Optional<BackpackSessionView> openSession(UUID playerId);
    Optional<BackpackSessionView> openSessionForBackpack(UUID backpackId);
    ItemStack create(String tierId);
    boolean open(Player player, ItemStack backpack);
}
