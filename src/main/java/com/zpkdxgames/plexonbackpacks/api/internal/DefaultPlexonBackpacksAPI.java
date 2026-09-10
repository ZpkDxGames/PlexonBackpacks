package com.zpkdxgames.plexonbackpacks.api.internal;

import com.zpkdxgames.plexonbackpacks.api.BackpackSessionView;
import com.zpkdxgames.plexonbackpacks.api.BackpackView;
import com.zpkdxgames.plexonbackpacks.api.PlexonBackpacksAPI;
import com.zpkdxgames.plexonbackpacks.api.TierView;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackHolder;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DefaultPlexonBackpacksAPI implements PlexonBackpacksAPI {
    private final ConfigManager config;
    private final BackpackItemFactory itemFactory;
    private final BackpackService service;

    public DefaultPlexonBackpacksAPI(
            ConfigManager config,
            BackpackItemFactory itemFactory,
            BackpackService service
    ) {
        this.config = config;
        this.itemFactory = itemFactory;
        this.service = service;
    }

    @Override
    public boolean isBackpack(ItemStack item) {
        return itemFactory.isBackpack(item);
    }

    @Override
    public Optional<UUID> backpackId(ItemStack item) {
        return itemFactory.backpackId(item);
    }

    @Override
    public Optional<String> tierId(ItemStack item) {
        return itemFactory.tierId(item);
    }

    @Override
    public Optional<UUID> owner(ItemStack item) {
        return itemFactory.owner(item);
    }

    @Override
    public Optional<BackpackView> backpack(UUID backpackId) {
        if (backpackId == null) {
            return Optional.empty();
        }
        return service.record(backpackId).map(this::view);
    }

    @Override
    public Collection<TierView> tiers() {
        return config.tiers().stream()
                .map(tier -> new TierView(tier.id(), tier.slots(), tier.permission(), tier.customModelData()))
                .toList();
    }

    @Override
    public Optional<BackpackSessionView> openSession(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return service.session(playerId).map(this::sessionView);
    }

    @Override
    public Optional<BackpackSessionView> openSessionForBackpack(UUID backpackId) {
        if (backpackId == null) {
            return Optional.empty();
        }
        return service.sessionByBackpack(backpackId).map(this::sessionView);
    }

    @Override
    public ItemStack create(String tierId) {
        requirePrimaryThread("create");
        TierDefinition tier = config.tier(tierId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown backpack tier: " + tierId));
        return service.createBackpack(tier);
    }

    @Override
    public boolean open(Player player, ItemStack backpack) {
        requirePrimaryThread("open");
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        return service.open(player, backpack);
    }

    private BackpackView view(BackpackRecord record) {
        int configuredSize = config.tier(record.tierId()).map(TierDefinition::slots).orElse(0);
        int size = configuredSize <= 0 ? record.contents().length : record.requiredSize(configuredSize);
        return new BackpackView(
                record.id(),
                record.tierId(),
                record.owner(),
                record.createdAt(),
                record.lastAccess(),
                size);
    }

    private BackpackSessionView sessionView(BackpackHolder holder) {
        return new BackpackSessionView(
                holder.viewerId(),
                holder.backpackId(),
                holder.tierId(),
                holder.sessionId());
    }

    private static void requirePrimaryThread(String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlexonBackpacks API " + operation + " must run on the primary server thread");
        }
    }
}
