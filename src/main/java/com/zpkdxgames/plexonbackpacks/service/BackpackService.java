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
    private final Map<UUID, DeferredForceClose> deferredForceCloses = new HashMap<>();
    private final BackpackGuiRenderer guiRenderer;
    private String lastForceCloseDiagnostic = "NONE";

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

        int capacity = normalizeAuthoritativeCapacity(record, tier);
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
        DeferredForceClose deferred = deferredForceCloses.get(holder.sessionId());
        if (deferred != null) {
            deferred.closeEventObserved = true;
        }
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
        if (deferred != null) {
            deferred.persistenceSucceeded = true;
            return true;
        }
        return finishSessionRelease(holder);
    }

    public boolean changePage(BackpackHolder holder, int targetPage) {
        requirePrimaryThread("page change");
        if (!isAuthoritative(holder) || targetPage < 0 || targetPage >= holder.pageCount()) {
            return false;
        }
        if (!requireClearCursor(holder)) {
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
        if (!isAuthoritative(holder) || !requireClearCursor(holder)) {
            return false;
        }
        syncVisiblePage(holder);
        BackpackRecord record = dataStore.find(holder.backpackId()).orElseThrow();
        ItemStack[] before = BackpackCapacityInvariant.normalize(record.contents(), holder.capacity());
        ItemStack[] sorted = new ItemStack[holder.capacity()];
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
                || !holder.viewerId().equals(player.getUniqueId()) || !requireClearCursor(player)) {
            return 0;
        }

        syncVisiblePage(holder);
        BackpackRecord record = dataStore.find(holder.backpackId()).orElseThrow();
        ItemStack[] recordBefore = BackpackCapacityInvariant.normalize(record.contents(), holder.capacity());
        ItemStack[] playerBefore = cloneArray(player.getInventory().getStorageContents());
        ItemStack[] target = BackpackCapacityInvariant.normalize(recordBefore, holder.capacity());
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
        TierDefinition current = config.tier(record.tierId()).orElse(null);
        TierDefinition next = config.nextTier(record.tierId()).orElse(null);
        if (current == null || next == null) {
            messages.send(player, current == null ? "invalid-backpack" : "max-tier");
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
        int previousCapacity = BackpackCapacityInvariant.authoritativeCapacity(record, current);
        ItemStack[] previousContents = BackpackCapacityInvariant.normalize(record.contents(), previousCapacity);
        try {
            int nextCapacity = BackpackCapacityInvariant.authoritativeCapacity(record, next);
            record.tierId(next.id());
            record.contents(BackpackCapacityInvariant.normalize(previousContents, nextCapacity));
            dataStore.markDirty(record.id());
            if (!dataStore.saveSync()) {
                throw new IllegalStateException("upgrade persistence commit failed");
            }
            refreshMatchingItems(player, record.id(), next, record.owner());
        } catch (RuntimeException exception) {
            record.tierId(previousTier);
            record.contents(previousContents);
            dataStore.markDirty(record.id());
            boolean rollbackPersisted = dataStore.saveSync();
            if (!economy.refund(player.getUniqueId(), cost)) {
                plugin.getLogger().severe("CRITICAL: Economy refund failed after backpack upgrade rollback for player "
                        + player.getUniqueId() + ", backpack " + record.id() + ", amount " + cost);
            }
            if (!rollbackPersisted) {
                plugin.getLogger().severe("CRITICAL: Backpack upgrade rollback could not be persisted for " + record.id());
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
            return forceCloseOutcome(true, "NO_ACTIVE_SESSION", backpackId,
                    "No authoritative session remained; force-close is idempotently complete.");
        }

        BackpackHolder indexedHolder = holdersByPlayer.get(session.playerId());
        if (indexedHolder != null && !matchesSession(indexedHolder, session)) {
            return forceCloseOutcome(false, "FAILED_INDEX_IDENTITY_MISMATCH", backpackId,
                    "The indexed holder does not match the authoritative session identity; registry lock retained.");
        }

        List<LiveBackpackView> liveViews = findLiveBackpackViews(backpackId);
        List<LiveBackpackView> authoritativeViews = new ArrayList<>();
        for (LiveBackpackView view : liveViews) {
            if (!matchesSession(view.holder(), session)
                    || !view.player().getUniqueId().equals(session.playerId())) {
                return forceCloseOutcome(false, "FAILED_AMBIGUOUS_LIVE_VIEW", backpackId,
                        "A live BackpackHolder with the same backpack UUID does not match the authoritative session; "
                                + "nothing was released.");
            }
            authoritativeViews.add(view);
        }
        if (authoritativeViews.size() > 1) {
            return forceCloseOutcome(false, "FAILED_MULTIPLE_LIVE_VIEWS", backpackId,
                    "Multiple live views claim the same authoritative session; registry lock retained for recovery.");
        }

        LiveBackpackView liveView = authoritativeViews.isEmpty() ? null : authoritativeViews.getFirst();
        if (indexedHolder != null && liveView != null && indexedHolder != liveView.holder()) {
            return forceCloseOutcome(false, "FAILED_INDEX_VIEW_MISMATCH", backpackId,
                    "The indexed holder object differs from the live authoritative holder; registry lock retained.");
        }

        if (indexedHolder == null && liveView != null) {
            holdersByPlayer.put(session.playerId(), liveView.holder());
            indexedHolder = liveView.holder();
            plugin.getLogger().warning("Rediscovered missing holder index for backpack " + backpackId
                    + " from live authoritative session " + session.sessionId() + '.');
        }

        if (liveView != null) {
            return closeRediscoveredLiveView(session, liveView);
        }

        if (indexedHolder != null) {
            boolean closed = close(indexedHolder, indexedHolder.getInventory());
            return forceCloseOutcome(closed,
                    closed ? "CLOSED_INDEXED_SESSION" : "FAILED_INDEXED_CLOSE",
                    backpackId,
                    closed
                            ? "Indexed session had no live GUI and was durably closed."
                            : "Indexed session could not be durably closed; registry lock retained.");
        }

        SessionRegistry.Session released = sessionRegistry.forceReleaseBackpack(backpackId).orElse(null);
        return forceCloseOutcome(released != null,
                released != null ? "STALE_REGISTRY_RELEASED" : "FAILED_STALE_REGISTRY_RELEASE",
                backpackId,
                released != null
                        ? "No live BackpackHolder existed; stale registry ownership was released."
                        : "Stale registry ownership changed during force-close; no unsafe release was attempted.");
    }

    public String lastForceCloseDiagnostic() {
        return lastForceCloseDiagnostic;
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

    private boolean closeRediscoveredLiveView(SessionRegistry.Session session, LiveBackpackView view) {
        BackpackHolder holder = view.holder();
        Player player = view.player();
        UUID backpackId = session.backpackId();
        if (deferredForceCloses.containsKey(session.sessionId())) {
            return forceCloseOutcome(false, "FAILED_REENTRANT_FORCE_CLOSE", backpackId,
                    "A force-close transaction is already active for this session; registry lock retained.");
        }
        if (player.getOpenInventory().getTopInventory() != view.inventory()) {
            return forceCloseOutcome(false, "FAILED_VIEW_CHANGED", backpackId,
                    "The player's live inventory changed before invalidation; registry lock retained.");
        }

        ItemStack cursorBefore = cloneItem(player.getItemOnCursor());
        DeferredForceClose deferred = new DeferredForceClose();
        deferredForceCloses.put(session.sessionId(), deferred);
        try {
            player.closeInventory();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not invalidate rediscovered live backpack view " + backpackId, exception);
            return forceCloseOutcome(false, "FAILED_LIVE_CLOSE_EXCEPTION", backpackId,
                    "Closing the rediscovered live view raised an exception; registry lock retained.");
        } finally {
            deferredForceCloses.remove(session.sessionId(), deferred);
        }

        if (!sameExactItem(cursorBefore, player.getItemOnCursor())) {
            return forceCloseOutcome(false, "FAILED_CURSOR_CHANGED", backpackId,
                    "Player cursor changed during forced live-view invalidation; registry lock retained.");
        }
        if (!deferred.closeEventObserved) {
            return forceCloseOutcome(false, "FAILED_CLOSE_EVENT_NOT_OBSERVED", backpackId,
                    "InventoryCloseEvent was not observed for the rediscovered view; registry lock retained.");
        }
        if (!deferred.persistenceSucceeded) {
            return forceCloseOutcome(false, "FAILED_LIVE_PERSISTENCE", backpackId,
                    "Live-view custody could not be persisted during close; registry lock retained.");
        }
        if (!findLiveBackpackViews(backpackId).isEmpty()) {
            return forceCloseOutcome(false, "FAILED_VIEW_STILL_OPEN", backpackId,
                    "A matching live BackpackHolder remains open after invalidation; registry lock retained.");
        }
        if (!finishSessionRelease(holder)) {
            return forceCloseOutcome(false, "FAILED_SESSION_RELEASE", backpackId,
                    "Live view closed and persisted, but authoritative registry release did not validate.");
        }
        return forceCloseOutcome(true, "REDISCOVERED_LIVE_VIEW_CLOSED", backpackId,
                "Live authoritative GUI was invalidated and persisted before registry ownership was released.");
    }

    private List<LiveBackpackView> findLiveBackpackViews(UUID backpackId) {
        List<LiveBackpackView> matches = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof BackpackHolder holder && holder.backpackId().equals(backpackId)) {
                matches.add(new LiveBackpackView(player, holder, top));
            }
        }
        return List.copyOf(matches);
    }

    private static boolean matchesSession(BackpackHolder holder, SessionRegistry.Session session) {
        return holder.backpackId().equals(session.backpackId())
                && holder.viewerId().equals(session.playerId())
                && holder.sessionId().equals(session.sessionId());
    }

    private boolean finishSessionRelease(BackpackHolder holder) {
        BackpackHolder active = holdersByPlayer.get(holder.viewerId());
        SessionRegistry.Session registered = sessionRegistry.byBackpack(holder.backpackId()).orElse(null);
        if (active != holder || registered == null || !matchesSession(holder, registered)) {
            plugin.getLogger().severe("Refusing inconsistent session release for backpack " + holder.backpackId());
            return false;
        }

        holdersByPlayer.remove(holder.viewerId(), holder);
        if (sessionRegistry.releaseByPlayer(holder.viewerId(), holder.sessionId()).isEmpty()) {
            holdersByPlayer.put(holder.viewerId(), holder);
            plugin.getLogger().severe("Registry release failed after validating holder " + holder.sessionId()
                    + "; holder index restored.");
            return false;
        }

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

    private boolean forceCloseOutcome(boolean success, String code, UUID backpackId, String detail) {
        lastForceCloseDiagnostic = code + " backpack=" + backpackId + " detail=" + detail;
        if (success) {
            plugin.getLogger().info("Force-close " + lastForceCloseDiagnostic);
        } else {
            plugin.getLogger().severe("Force-close " + lastForceCloseDiagnostic);
        }
        return success;
    }

    private boolean isAuthoritative(BackpackHolder holder) {
        return holdersByPlayer.get(holder.viewerId()) == holder
                && sessionRegistry.byBackpack(holder.backpackId())
                .filter(session -> session.sessionId().equals(holder.sessionId()))
                .isPresent();
    }

    private int normalizeAuthoritativeCapacity(BackpackRecord record, TierDefinition tier) {
        int capacity = BackpackCapacityInvariant.authoritativeCapacity(record, tier);
        ItemStack[] current = record.contents();
        if (!BackpackCapacityInvariant.isNormalized(current, capacity)) {
            record.contents(BackpackCapacityInvariant.normalize(current, capacity));
            dataStore.markDirty(record.id());
        }
        return capacity;
    }

    private void syncVisiblePage(BackpackHolder holder) {
        BackpackRecord record = dataStore.find(holder.backpackId())
                .orElseThrow(() -> new IllegalStateException("Authoritative backpack record is missing"));
        ItemStack[] previous = BackpackCapacityInvariant.normalize(record.contents(), holder.capacity());
        ItemStack[] merged = BackpackCapacityInvariant.normalize(previous, holder.capacity());
        ItemStack[] visible = holder.visibleStorageContents();
        int start = holder.page() * 45;
        for (int guiSlot = 0; guiSlot < visible.length; guiSlot++) {
            ItemStack item = visible[guiSlot];
            int recordSlot = start + guiSlot;
            ItemStack persisted = previous[recordSlot];
            if (BackpackNestingPolicy.containsBackpack(item, itemFactory)
                    && !sameExactLegacyNestedItem(persisted, item)) {
                throw new IllegalStateException("New nested backpack insertion detected in visible storage slot "
                        + guiSlot);
            }
            merged[recordSlot] = item == null ? null : item.clone();
        }
        record.contents(merged);
        record.lastAccess(System.currentTimeMillis());
        dataStore.markDirty(record.id());
        holder.resetSnapshotHash();
    }

    private boolean requireClearCursor(BackpackHolder holder) {
        Player player = Bukkit.getPlayer(holder.viewerId());
        return player != null && requireClearCursor(player);
    }

    private boolean requireClearCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return true;
        }
        messages.send(player, "cursor-busy");
        return false;
    }

    private boolean sameExactLegacyNestedItem(ItemStack persisted, ItemStack visible) {
        return BackpackNestingPolicy.containsBackpack(persisted, itemFactory)
                && persisted != null
                && persisted.equals(visible);
    }

    private static boolean sameExactItem(ItemStack left, ItemStack right) {
        boolean leftEmpty = left == null || left.getType().isAir();
        boolean rightEmpty = right == null || right.getType().isAir();
        if (leftEmpty || rightEmpty) {
            return leftEmpty == rightEmpty;
        }
        return left.equals(right);
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
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

    private record LiveBackpackView(Player player, BackpackHolder holder, Inventory inventory) {
    }

    private static final class DeferredForceClose {
        private boolean closeEventObserved;
        private boolean persistenceSucceeded;
    }
}
