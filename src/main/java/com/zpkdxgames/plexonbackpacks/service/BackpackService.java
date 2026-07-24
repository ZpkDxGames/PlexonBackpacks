package com.zpkdxgames.plexonbackpacks.service;

import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackHolder;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.storage.BackpackDataStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BackpackService {
    private final ConfigManager config;
    private final Messages messages;
    private final BackpackItemFactory itemFactory;
    private final BackpackDataStore dataStore;
    private final Map<UUID, UUID> locks = new HashMap<>();
    private final Map<UUID, BackpackHolder> sessionsByPlayer = new HashMap<>();

    public BackpackService(
            ConfigManager config,
            Messages messages,
            BackpackItemFactory itemFactory,
            BackpackDataStore dataStore
    ) {
        this.config = config;
        this.messages = messages;
        this.itemFactory = itemFactory;
        this.dataStore = dataStore;
    }

    public boolean open(Player player, ItemStack item) {
        Optional<UUID> optionalId = itemFactory.backpackId(item);
        Optional<String> optionalTierId = itemFactory.tierId(item);
        if (optionalId.isEmpty() || optionalTierId.isEmpty()) {
            messages.send(player, "held-required");
            return false;
        }

        TierDefinition itemTier = config.tier(optionalTierId.get()).orElse(null);
        if (itemTier == null) {
            messages.send(player, "invalid-backpack");
            return false;
        }
        if (sessionsByPlayer.containsKey(player.getUniqueId())) {
            messages.send(player, "already-open");
            return false;
        }

        UUID backpackId = optionalId.get();
        UUID existingLock = locks.get(backpackId);
        if (existingLock != null && !existingLock.equals(player.getUniqueId())) {
            messages.send(player, "in-use");
            return false;
        }

        BackpackRecord record = dataStore.find(backpackId).orElseGet(() -> dataStore.register(
                backpackId,
                itemTier.id(),
                itemFactory.owner(item).orElse(null),
                itemTier.slots()
        ));
        TierDefinition tier = config.tier(record.tierId()).orElse(null);
        if (tier == null) {
            messages.send(player, "invalid-backpack");
            return false;
        }
        if (!tier.permission().isBlank() && !player.hasPermission(tier.permission())) {
            messages.send(player, "no-permission");
            return false;
        }

        if (config.ownershipEnabled()) {
            if (record.owner() == null && config.bindOnFirstOpen()) {
                record.owner(player.getUniqueId());
                dataStore.markDirty();
                refreshMatchingItems(player, record.id(), tier, record.owner());
                messages.send(player, "bound");
            } else if (record.owner() != null
                    && !record.owner().equals(player.getUniqueId())
                    && !player.hasPermission("plexonbackpacks.bypass-owner")) {
                messages.send(player, "wrong-owner", "owner", itemFactory.ownerName(record.owner()));
                return false;
            }
        }

        int inventorySize = record.requiredSize(tier.slots());
        BackpackHolder holder = new BackpackHolder(record.id(), player.getUniqueId(), tier.id());
        Inventory inventory = Bukkit.createInventory(
                holder,
                inventorySize,
                config.component(tier.inventoryTitle(), "tier", itemFactory.plainTierName(tier))
        );
        holder.inventory(inventory);

        ItemStack[] contents = record.contents();
        for (int slot = 0; slot < Math.min(contents.length, inventory.getSize()); slot++) {
            inventory.setItem(slot, contents[slot]);
        }

        locks.put(record.id(), player.getUniqueId());
        sessionsByPlayer.put(player.getUniqueId(), holder);
        record.lastAccess(System.currentTimeMillis());
        dataStore.markDirty();
        player.openInventory(inventory);
        return true;
    }

    public ItemStack createBackpack(TierDefinition tier, UUID owner) {
        ItemStack item = itemFactory.create(tier, owner);
        UUID id = itemFactory.backpackId(item).orElseThrow();
        dataStore.register(id, tier.id(), owner, tier.slots());
        return item;
    }

    public void close(BackpackHolder holder, Inventory inventory) {
        if (!sessionsByPlayer.remove(holder.viewerId(), holder)) {
            return;
        }
        locks.remove(holder.backpackId(), holder.viewerId());
        dataStore.find(holder.backpackId()).ifPresent(record -> {
            record.contents(inventory.getContents());
            record.lastAccess(System.currentTimeMillis());
            dataStore.markDirty();
        });
    }

    public void snapshotOpenSessions() {
        for (BackpackHolder holder : sessionsByPlayer.values()) {
            dataStore.find(holder.backpackId()).ifPresent(record -> {
                record.contents(holder.getInventory().getContents());
                record.lastAccess(System.currentTimeMillis());
            });
        }
        if (!sessionsByPlayer.isEmpty()) {
            dataStore.markDirty();
        }
    }

    public void closeAll() {
        for (UUID playerId : sessionsByPlayer.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
            } else {
                BackpackHolder holder = sessionsByPlayer.get(playerId);
                if (holder != null) {
                    close(holder, holder.getInventory());
                }
            }
        }
    }

    public Optional<BackpackHolder> session(UUID playerId) {
        return Optional.ofNullable(sessionsByPlayer.get(playerId));
    }

    public Optional<BackpackRecord> record(UUID backpackId) {
        return dataStore.find(backpackId);
    }

    private void refreshMatchingItems(Player player, UUID backpackId, TierDefinition tier, UUID owner) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate == null) {
                continue;
            }
            if (itemFactory.backpackId(candidate).filter(backpackId::equals).isPresent()) {
                itemFactory.refresh(candidate, tier, owner);
                player.getInventory().setItem(slot, candidate);
            }
        }
    }
}
