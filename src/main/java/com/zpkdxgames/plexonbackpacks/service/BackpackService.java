package com.zpkdxgames.plexonbackpacks.service;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.event.PlexonBackpackBoundEvent;
import com.zpkdxgames.plexonbackpacks.event.PlexonBackpackClosedEvent;
import com.zpkdxgames.plexonbackpacks.event.PlexonBackpackOpenedEvent;
import com.zpkdxgames.plexonbackpacks.event.PlexonBackpackUpgradedEvent;
import com.zpkdxgames.plexonbackpacks.integration.economy.EconomyGateway;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackGuiRenderer;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackHolder;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.item.BackpackNestingPolicy;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.storage.BackpackDataStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BackpackService {
    private final PlexonBackpacksPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final BackpackItemFactory itemFactory;
    private final BackpackDataStore dataStore;
    private final EconomyGateway economy;
    private final SessionRegistry sessionRegistry = new SessionRegistry();
    private final Map<UUID, BackpackHolder> holdersByPlayer = new HashMap<>();
    private final BackpackGuiRenderer guiRenderer;

    public BackpackService(
            PlexonBackpacksPlugin plugin,
            ConfigManager config,
            Messages messages,
            BackpackItemFactory itemFactory,
            BackpackDataStore dataStore,
            EconomyGateway economy
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.itemFactory = itemFactory;
        this.dataStore = dataStore;
        this.economy = economy;
        this.guiRenderer = new BackpackGuiRenderer(config, itemFactory);
    }

    public boolean open(Player player, ItemStack item) {
        requirePrimaryThread("open");

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
        if (sessionRegistry.byPlayer(player.getUniqueId()).isPresent()) {
            messages.send(player, "already-open");
            return false;
        }

        UUID backpackId = optionalId.get();
        BackpackRecord record = dataStore.find(backpackId).orElse(null);
        if (record == null) {
            if (itemFactory.hasCurrentSchema(item)) {
                plugin.getLogger().severe("Refusing to recreate missing Phase 2 backpack state for " + backpackId);
                messages.send(player, "missing-state");
                return false;
            }
            record = dataStore.register(
                    backpackId,
                    itemTier.id(),
                    itemFactory.owner(item).orElse(null),
                    itemTier.slots()
            );
            if (!dataStore.saveSync()) {
                messages.send(player, "persistence-failed");
                return false;
            }
            plugin.getLogger().info("Adopted legacy 1.x backpack reference " + backpackId + " into Phase 2 state.");
        }

        TierDefinition tier = config.tier(record.tierId()).orElse(null);
        if (tier == null) {
            messages.send(player, "invalid-backpack");
            return false;
        }
        if (!tier.permission().isBlank() && !player.hasPermission(tier.permission())) {
            messages.send(player, "no-permission");
            return false;
        }

        boolean bindAfterReservation = false;
        if (config.ownershipEnabled()) {
            if (record.owner() == null && config.bindOnFirstOpen()) {
                bindAfterReservation = true;
            } else if (record.owner() != null
                    && !record.owner().equals(player.getUniqueId())
                    && !player.hasPermission("plexonbackpacks.bypass-owner")) {
                messages.send(player, "wrong-owner", "owner", itemFactory.ownerName(record.owner()));
                return false;
            }
        }

        int capacity = record.requiredSize(tier.slots());
        UUID sessionId = UUID.randomUUID();
        SessionRegistry.ReservationResult reservation = sessionRegistry.reserve(
                player.getUniqueId(), backpackId, sessionId);
        if (reservation == SessionRegistry.ReservationResult.PLAYER_BUSY) {
            messages.send(player, "already-open");
            return false;
        }
        if (reservation == SessionRegistry.ReservationResult.BACKPACK_BUSY) {
            messages.send(player, "in-use");
            return false;
        }

        BackpackHolder holder = new BackpackHolder(record.id(), player.getUniqueId(), tier.id(), sessionId, capacity);
        holdersByPlayer.put(player.getUniqueId(), holder);

        if (bindAfterReservation) {
            UUID previousOwner = record.owner();
            record.owner(player.getUniqueId());
            dataStore.markDirty(record.id());
            if (!dataStore.saveSync()) {
                record.owner(previousOwner);
                dataStore.markDirty(record.id());
                rollbackOpen(holder);
                messages.send(player, "persistence-failed");
                return false;
            }
            refreshMatchingItems(player, record.id(), tier, record.owner());
            messages.send(player, "bound");
            fireEventSafely(new PlexonBackpackBoundEvent(
                    player, record.id(), tier.id(), record.owner(), eventId(sessionId, "bind"), sessionId));
        } else {
            refreshMatchingItems(player, record.id(), tier, record.owner());
        }

        Inventory inventory = guiRenderer.create(holder, record, tier);
        try {
            player.openInventory(inventory);
        } catch (RuntimeException exception) {
            rollbackOpen(holder);
            plugin.getLogger().log(Level.WARNING, "Could not open backpack inventory " + record.id(), exception);
            return false;
        }
        if (player.getOpenInventory().getTopInventory() != inventory) {
            rollbackOpen(holder);
            return false;
        }

        record.lastAccess(System.currentTimeMillis());
        dataStore.markDirty(record.id());
        fireEventSafely(new PlexonBackpackOpenedEvent(
                player,
                record.id(),
                tier.id(),
                record.owner(),
                capacity,
                eventId(sessionId, "open"),
                sessionId));
        return true;
    }

    public ItemStack createBackpack(TierDefinition tier) {
        requirePrimaryThread("create");
        ItemStack item = itemFactory.create(tier, null);
        UUID id = itemFactory.backpackId(item).orElseThrow();
        dataStore.register(id, tier.id(), null, tier.slots());
        if (!dataStore.saveSync()) {
            throw new IllegalStateException("Could not persist new backpack " + id + " before custody transfer");
        }
        return item;
    }

    public boolean ensureRegisteredCraft(ItemStack result, TierDefinition tier) {
        requirePrimaryThread("craft registration");
        UUID id = itemFactory.backpackId(result).orElse(null);
        if (id == null) {
            return false;
        }
        BackpackRecord existing = dataStore.find(id).orElse(null);
        if (existing != null) {
            return existing.tierId().equalsIgnoreCase(tier.id());
        }
        dataStore.register(id, tier.id(), null, tier.slots());
        return dataStore.saveSync();
    }

    public boolean close(BackpackHolder holder, Inventory inventory) {
        requirePrimaryThread("close");
        BackpackHolder active = holdersByPlayer.get(holder.viewerId());
        if (active != holder) {
            return false;
        }
        try {
            syncVisiblePage(holder);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Refusing unsafe close for backpack " + holder.backpackId(), exception);
            return false;
        }
        if (!dataStore.saveSync()) {
            Player player = Bukkit.getPlayer(holder.viewerId());
            if (player != null) {
                messages.send(player, "persistence-failed");
            }
            plugin.getLogger().severe("Backpack " + holder.backpackId()
                    + " remains session-locked because close persistence failed.");
            return false;
        }

        holdersByPlayer.remove(holder.viewerId(), holder);
        sessionRegistry.releaseByPlayer(holder.viewerId(), holder.sessionId());
        BackpackRecord record = dataStore.find(holder.backpackId()).orElse(null);
        Player player = Bukkit.getPlayer(holder.viewerId());
        if (record != null && player != null) {
            fireEventSafely(new PlexonBackpackClosedEvent(
                    player,
                    record.id(),
                    record.tierId(),
                    record.owner(),
                    true,
                    eventId(holder.sessionId(), "close"),
                    holder.sessionId()));
        }
        return true;
    }

    public boolean changePage(BackpackHolder holder, int targetPage) {
        requirePrimaryThread("page change");
        if (!isAuthoritative(holder) || targetPage < 0 || targetPage >= holder.pageCount()) {
            return false;
        }
        syncVisiblePage(holder);
        dataStore.requestSave();
        holder.page(targetPage);
        BackpackRecord record = dataStore.find(holder.backpackId()).orElseThrow();
        TierDefinition tier = config.tier(record.tierId()).orElseThrow();
        guiRenderer.render(holder, record, tier);
        return true;
    }

    public boolean sort(BackpackHolder holder) {
        requirePrimaryThread("sort");
        if (!isAuthoritative(holder)) {
            return false;
        }
        syncVisiblePage(holder);
        BackpackRecord record = dataStore.find(holder.backpackId()).orElseThrow();
        ItemStack[] before = record.contents();
        ItemStack[] sorted = new ItemStack[Math.max(holder.capacity(), before.length)];
        List<ItemStack> occupied = Arrays.stream(before)
                .filter(item -> item != null && !item.getType().isAir())
                .map(ItemStack::clone)
                .sorted(Comparator.comparing((ItemStack item) -> item.getType().name())
                        .thenComparingInt(ItemStack::getAmount))
                .toList();
        for (int slot = 0; slot < occupied.size(); slot++) {
            sorted[slot] = occupied.get(slot);
        }
        record.contents(sorted);
        dataStore.markDirty(record.id());
        if (!dataStore.saveSync()) {
            record.contents(before);
            dataStore.markDirty(record.id());
            guiRenderer.render(holder, record, config.tier(record.tierId()).orElseThrow());
            return false;
        }
        guiRenderer.render(holder, record, config.tier(record.tierId()).orElseThrow());
        return true;
    }

    public int quickDeposit(Player player, BackpackHolder holder) {
        requirePrimaryThread("quick deposit");
        if (!config.quickDepositEnabled() || !isAuthoritative(holder)
                || !holder.viewerId().equals(player.getUniqueId())) {
            return 0;
        }

        syncVisiblePage(holder);
        BackpackRecord record = dataStore.find(holder.backpackId()).orElseThrow();
        ItemStack[] recordBefore = record.contents();
        ItemStack[] playerBefore = cloneArray(player.getInventory().getStorageContents());
        ItemStack[] target = Arrays.copyOf(recordBefore, Math.max(holder.capacity(), recordBefore.length));
        ItemStack[] playerAfter = cloneArray(playerBefore);
        int moved = 0;

        for (int slot = 0; slot < playerAfter.length; slot++) {
            ItemStack source = playerAfter[slot];
            if (source == null || source.getType().isAir()
                    || BackpackNestingPolicy.containsBackpack(source, itemFactory)) {
                continue;
            }
            int beforeAmount = source.getAmount();
            ItemStack remainder = mergeInto(target, source);
            int remainderAmount = remainder == null ? 0 : remainder.getAmount();
            moved += beforeAmount - remainderAmount;
            playerAfter[slot] = remainder;
        }

        if (moved == 0) {
            return 0;
        }

        try {
            record.contents(target);
            player.getInventory().setStorageContents(playerAfter);
            dataStore.markDirty(record.id());
            if (!dataStore.saveSync()) {
                throw new IllegalStateException("persistence commit failed");
            }
        } catch (RuntimeException exception) {
            record.contents(recordBefore);
            player.getInventory().setStorageContents(playerBefore);
            dataStore.markDirty(record.id());
            plugin.getLogger().log(Level.SEVERE, "Quick-deposit transaction rolled back for " + record.id(), exception);
            guiRenderer.render(holder, record, config.tier(record.tierId()).orElseThrow());
            return 0;
        }

        guiRenderer.render(holder, record, config.tier(record.tierId()).orElseThrow());
        return moved;
    }

    public boolean upgrade(Player player, ItemStack reference) {
        requirePrimaryThread("upgrade");
        if (!config.upgradesEnabled()) {
            messages.send(player, "upgrades-disabled");
            return false;
        }
        UUID backpackId = itemFactory.backpackId(reference).orElse(null);
        if (backpackId == null) {
            messages.send(player, "held-required");
            return false;
        }
        if (sessionRegistry.byBackpack(backpackId).isPresent()) {
            messages.send(player, "upgrade-open");
            return false;
        }
        BackpackRecord record = dataStore.find(backpackId).orElse(null);
        if (record == null) {
            messages.send(player, "missing-state");
            return false;
        }
        if (config.ownershipEnabled() && record.owner() != null
                && !record.owner().equals(player.getUniqueId())
                && !player.hasPermission("plexonbackpacks.bypass-owner")) {
            messages.send(player, "wrong-owner", "owner", itemFactory.ownerName(record.owner()));
            return false;
        }
        TierDefinition next = config.nextTier(record.tierId()).orElse(null);
        if (next == null) {
            messages.send(player, "max-tier");
            return false;
        }
        if (!next.permission().isBlank() && !player.hasPermission(next.permission())) {
            messages.send(player, "no-permission");
            return false;
        }

        double cost = next.upgradeCost();
        if (cost > 0.0D && !economy.available()) {
            messages.send(player, "economy-unavailable");
            return false;
        }
        if (!economy.has(player.getUniqueId(), cost)) {
            messages.send(player, "upgrade-insufficient", "cost", formatCost(cost));
            return false;
        }
        if (!economy.withdraw(player.getUniqueId(), cost)) {
            messages.send(player, "upgrade-charge-failed");
            return false;
        }

        String previousTier = record.tierId();
        try {
            record.tierId(next.id());
            dataStore.markDirty(record.id());
            if (!dataStore.saveSync()) {
                throw new IllegalStateException("upgrade persistence commit failed");
            }
            refreshMatchingItems(player, record.id(), next, record.owner());
        } catch (RuntimeException exception) {
            record.tierId(previousTier);
            dataStore.markDirty(record.id());
            dataStore.saveSync();
            if (!economy.refund(player.getUniqueId(), cost)) {
                plugin.getLogger().severe("CRITICAL: Economy refund failed after backpack upgrade rollback for player "
                        + player.getUniqueId() + ", backpack " + record.id() + ", amount " + cost);
            }
            plugin.getLogger().log(Level.SEVERE, "Backpack upgrade rolled back for " + record.id(), exception);
            messages.send(player, "upgrade-failed");
            return false;
        }

        messages.send(player, "upgrade-success",
                "tier", itemFactory.plainTierName(next),
                "slots", Integer.toString(next.slots()),
                "cost", formatCost(cost));
        fireEventSafely(new PlexonBackpackUpgradedEvent(
                player, record.id(), previousTier, next.id(), cost));
        return true;
    }

    public Optional<ItemStack> recoverReference(UUID backpackId) {
        requirePrimaryThread("recover reference");
        if (sessionRegistry.byBackpack(backpackId).isPresent()) {
            return Optional.empty();
        }
        return dataStore.find(backpackId).flatMap(record -> config.tier(record.tierId())
                .map(tier -> itemFactory.create(tier, record.id(), record.owner())));
    }

    public boolean repairReference(ItemStack item) {
        requirePrimaryThread("repair reference");
        UUID id = itemFactory.backpackId(item).orElse(null);
        if (id == null) {
            return false;
        }
        BackpackRecord record = dataStore.find(id).orElse(null);
        if (record == null) {
            return false;
        }
        TierDefinition tier = config.tier(record.tierId()).orElse(null);
        if (tier == null) {
            return false;
        }
        itemFactory.refresh(item, tier, record.owner());
        return true;
    }

    public boolean forceClose(UUID backpackId) {
        requirePrimaryThread("force close");
        SessionRegistry.Session session = sessionRegistry.byBackpack(backpackId).orElse(null);
        if (session == null) {
            return true;
        }
        BackpackHolder holder = holdersByPlayer.get(session.playerId());
        if (holder == null) {
            return false;
        }
        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null && player.getOpenInventory().getTopInventory() == holder.getInventory()) {
            player.closeInventory();
            return sessionRegistry.byBackpack(backpackId).isEmpty();
        }
        return close(holder, holder.getInventory());
    }

    public void snapshotOpenSessions() {
        requirePrimaryThread("snapshot sessions");
        for (BackpackHolder holder : List.copyOf(holdersByPlayer.values())) {
            if (!isAuthoritative(holder) || !holder.contentsChanged()) {
                continue;
            }
            syncVisiblePage(holder);
        }
    }

    public void closeAll() {
        requirePrimaryThread("close all");
        for (BackpackHolder holder : List.copyOf(holdersByPlayer.values())) {
            Player player = Bukkit.getPlayer(holder.viewerId());
            if (player != null && player.getOpenInventory().getTopInventory() == holder.getInventory()) {
                player.closeInventory();
            } else {
                close(holder, holder.getInventory());
            }
        }
    }

    public Optional<BackpackHolder> session(UUID playerId) {
        return Optional.ofNullable(holdersByPlayer.get(playerId));
    }

    public Optional<BackpackHolder> sessionByBackpack(UUID backpackId) {
        return sessionRegistry.byBackpack(backpackId)
                .map(SessionRegistry.Session::playerId)
                .map(holdersByPlayer::get);
    }

    public Optional<BackpackRecord> record(UUID backpackId) {
        return dataStore.find(backpackId);
    }

    public int openSessionCount() {
        return sessionRegistry.sessionCount();
    }

    public int activeLockCount() {
        return sessionRegistry.lockCount();
    }

    public String economyProvider() {
        return economy.providerName();
    }

    private boolean isAuthoritative(BackpackHolder holder) {
        return holdersByPlayer.get(holder.viewerId()) == holder
                && sessionRegistry.byBackpack(holder.backpackId())
                .filter(session -> session.sessionId().equals(holder.sessionId()))
                .isPresent();
    }

    private void syncVisiblePage(BackpackHolder holder) {
        BackpackRecord record = dataStore.find(holder.backpackId())
                .orElseThrow(() -> new IllegalStateException("Authoritative backpack record is missing"));
        ItemStack[] previous = record.contents();
        ItemStack[] merged = Arrays.copyOf(previous, Math.max(holder.capacity(), previous.length));
        ItemStack[] visible = holder.visibleStorageContents();
        int start = holder.page() * 45;
        for (int guiSlot = 0; guiSlot < visible.length; guiSlot++) {
            ItemStack item = visible[guiSlot];
            if (BackpackNestingPolicy.containsBackpack(item, itemFactory)) {
                throw new IllegalStateException("Nested backpack detected in visible storage slot " + guiSlot);
            }
            merged[start + guiSlot] = item == null ? null : item.clone();
        }
        record.contents(merged);
        record.lastAccess(System.currentTimeMillis());
        dataStore.markDirty(record.id());
        holder.resetSnapshotHash();
    }

    private static ItemStack mergeInto(ItemStack[] target, ItemStack source) {
        ItemStack remainder = source.clone();
        for (int slot = 0; slot < target.length && remainder.getAmount() > 0; slot++) {
            ItemStack existing = target[slot];
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(remainder)) {
                continue;
            }
            int max = Math.min(existing.getMaxStackSize(), remainder.getMaxStackSize());
            int space = max - existing.getAmount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remainder.getAmount());
            ItemStack updated = existing.clone();
            updated.setAmount(existing.getAmount() + moved);
            target[slot] = updated;
            remainder.setAmount(remainder.getAmount() - moved);
        }
        for (int slot = 0; slot < target.length && remainder.getAmount() > 0; slot++) {
            ItemStack existing = target[slot];
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int moved = Math.min(remainder.getMaxStackSize(), remainder.getAmount());
            ItemStack inserted = remainder.clone();
            inserted.setAmount(moved);
            target[slot] = inserted;
            remainder.setAmount(remainder.getAmount() - moved);
        }
        return remainder.getAmount() == 0 ? null : remainder;
    }

    private void rollbackOpen(BackpackHolder holder) {
        holdersByPlayer.remove(holder.viewerId(), holder);
        sessionRegistry.releaseByPlayer(holder.viewerId(), holder.sessionId());
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

    private void fireEventSafely(Event event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "An external listener failed while handling " + event.getEventName()
                            + "; backpack state remains committed.", exception);
        }
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        return Arrays.stream(source)
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }

    private static String formatCost(double cost) {
        return cost == Math.rint(cost) ? Long.toString((long) cost) : String.format(java.util.Locale.ROOT, "%.2f", cost);
    }

    private static void requirePrimaryThread(String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Backpack " + operation + " must run on the primary server thread");
        }
    }

    private static String eventId(UUID sessionId, String phase) {
        return sessionId + ":" + phase;
    }
}
