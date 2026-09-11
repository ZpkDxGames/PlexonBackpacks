package com.zpkdxgames.plexonbackpacks.inventory;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.integration.economy.EconomyGateway;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.service.BackpackCapacityInvariant;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Player-facing Backpack Info and upgrade preview. It never owns storage or economy mutation. */
public final class BackpackInfoGui {
    public static final int SIZE = 27;
    public static final int SUMMARY_SLOT = 4;
    public static final int CURRENT_SLOT = 11;
    public static final int NEXT_SLOT = 13;
    public static final int UPGRADE_SLOT = 15;
    public static final int RESTRICTIONS_SLOT = 20;
    public static final int BACK_SLOT = 22;
    public static final int CLOSE_SLOT = 26;

    private final PlexonBackpacksPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final BackpackItemFactory itemFactory;
    private final BackpackService service;
    private final EconomyGateway economy;

    public BackpackInfoGui(
            PlexonBackpacksPlugin plugin,
            ConfigManager config,
            Messages messages,
            BackpackItemFactory itemFactory,
            BackpackService service,
            EconomyGateway economy
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.itemFactory = itemFactory;
        this.service = service;
        this.economy = economy;
    }

    public void openForReference(Player player, ItemStack reference) {
        UUID backpackId = itemFactory.backpackId(reference).orElse(null);
        if (backpackId == null) {
            messages.send(player, "held-required");
            return;
        }
        BackpackHolder open = service.session(player.getUniqueId()).orElse(null);
        if (open != null) {
            if (!open.backpackId().equals(backpackId)) {
                messages.send(player, "already-open");
                return;
            }
            openFromStorage(player, open);
            return;
        }
        open(player, backpackId, 0);
    }

    public void openFromStorage(Player player, BackpackHolder holder) {
        UUID backpackId = holder.backpackId();
        int returnPage = holder.page();
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (service.session(player.getUniqueId()).isPresent()) {
                messages.send(player, "persistence-failed");
                return;
            }
            open(player, backpackId, returnPage);
        });
    }

    public void open(Player player, UUID backpackId, int returnPage) {
        BackpackRecord record = service.record(backpackId).orElse(null);
        if (record == null) {
            messages.send(player, "missing-state");
            return;
        }
        TierDefinition current = config.tier(record.tierId()).orElse(null);
        if (current == null) {
            messages.send(player, "invalid-backpack");
            return;
        }

        ItemStack reference = findReference(player, backpackId);
        UpgradeViewModel upgrade = upgradeView(player, record, current, reference != null);
        int capacity = BackpackCapacityInvariant.authoritativeCapacity(record, current);
        int used = usedSlots(record.contents(), capacity);
        BackpackViewModel backpack = new BackpackViewModel(
                itemFactory.plainTierName(current),
                itemFactory.ownerName(record.owner()),
                capacity,
                used,
                0,
                BackpackLayout.pageCount(capacity),
                record.owner() != null && !record.owner().equals(player.getUniqueId()),
                config.quickDepositEnabled());

        BackpackInfoHolder holder = new BackpackInfoHolder(backpackId, record.tierId(), returnPage);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                config.component("<dark_gray>Backpack Info <gray>•</gray> <white><tier></white>",
                        "tier", backpack.tierName()));
        holder.attach(inventory);
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of(), false);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        inventory.setItem(SUMMARY_SLOT, summary(backpack));
        inventory.setItem(CURRENT_SLOT, item(Material.CHEST,
                config.component("<aqua><bold>Current Tier</bold>"),
                List.of(
                        config.component("<gray>Tier: <white><tier></white>", "tier", upgrade.currentTier()),
                        config.component("<gray>Capacity: <white><slots></white> slots",
                                "slots", Integer.toString(upgrade.currentCapacity())),
                        config.component("<gray>Used: <white><used></white> <dark_gray>•</dark_gray> <gray>Free: <white><free></white>",
                                "used", Integer.toString(backpack.usedSlots()),
                                "free", Integer.toString(backpack.freeSlots()))
                ), false));
        inventory.setItem(NEXT_SLOT, nextTier(upgrade));
        inventory.setItem(UPGRADE_SLOT, upgradeAction(upgrade));
        inventory.setItem(RESTRICTIONS_SLOT, restrictions(backpack));
        inventory.setItem(BACK_SLOT, item(Material.DARK_OAK_DOOR,
                config.component("<gray><bold>Back to Storage</bold>"),
                List.of(config.component("<gray>Return to this backpack safely.</gray>")), false));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                config.component("<red><bold>Close</bold>"), List.of(), false));
        player.openInventory(inventory);
    }

    public void handleClick(Player player, BackpackInfoHolder holder, int rawSlot) {
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (rawSlot == BACK_SLOT) {
            reopenStorage(player, holder);
            return;
        }
        if (rawSlot != UPGRADE_SLOT) {
            return;
        }

        BackpackRecord record = service.record(holder.backpackId()).orElse(null);
        if (record == null) {
            messages.send(player, "missing-state");
            player.closeInventory();
            return;
        }
        if (!record.tierId().equalsIgnoreCase(holder.expectedTierId())) {
            messages.send(player, "backpack-changed");
            open(player, holder.backpackId(), holder.returnPage());
            return;
        }
        TierDefinition current = config.tier(record.tierId()).orElse(null);
        ItemStack reference = findReference(player, holder.backpackId());
        if (current == null || reference == null) {
            messages.send(player, reference == null ? "reference-required" : "invalid-backpack");
            open(player, holder.backpackId(), holder.returnPage());
            return;
        }
        UpgradeViewModel preview = upgradeView(player, record, current, true);
        if (!preview.ready()) {
            open(player, holder.backpackId(), holder.returnPage());
            return;
        }
        if (!holder.trySubmit()) {
            return;
        }
        service.upgrade(player, reference);
        open(player, holder.backpackId(), holder.returnPage());
    }

    UpgradeViewModel upgradeView(Player player, BackpackRecord record, TierDefinition current, boolean referencePresent) {
        int currentCapacity = BackpackCapacityInvariant.authoritativeCapacity(record, current);
        TierDefinition next = config.nextTier(current.id()).orElse(null);
        if (!config.upgradesEnabled()) {
            return new UpgradeViewModel(itemFactory.plainTierName(current), currentCapacity, "", 0, 0.0D,
                    UpgradeViewModel.Status.UPGRADES_DISABLED);
        }
        if (record.owner() != null && !record.owner().equals(player.getUniqueId())
                && !player.hasPermission("plexonbackpacks.bypass-owner")) {
            return new UpgradeViewModel(itemFactory.plainTierName(current), currentCapacity, "", 0, 0.0D,
                    UpgradeViewModel.Status.WRONG_OWNER);
        }
        if (service.authoritativeSession(record.id()).isPresent()) {
            return new UpgradeViewModel(itemFactory.plainTierName(current), currentCapacity, "", 0, 0.0D,
                    UpgradeViewModel.Status.BACKPACK_OPEN);
        }
        if (next == null) {
            return new UpgradeViewModel(itemFactory.plainTierName(current), currentCapacity, "", currentCapacity, 0.0D,
                    UpgradeViewModel.Status.MAX_TIER);
        }
        int nextCapacity = Math.max(currentCapacity, next.slots());
        double cost = next.upgradeCost();
        UpgradeViewModel.Status status;
        if (!player.hasPermission("plexonbackpacks.upgrade")
                || (!next.permission().isBlank() && !player.hasPermission(next.permission()))) {
            status = UpgradeViewModel.Status.MISSING_REQUIREMENT;
        } else if (!referencePresent) {
            status = UpgradeViewModel.Status.REFERENCE_MISSING;
        } else if (cost > 0.0D && !economy.available()) {
            status = UpgradeViewModel.Status.ECONOMY_UNAVAILABLE;
        } else if (cost > 0.0D && !economy.has(player.getUniqueId(), cost)) {
            status = UpgradeViewModel.Status.INSUFFICIENT_FUNDS;
        } else {
            status = UpgradeViewModel.Status.READY;
        }
        return new UpgradeViewModel(
                itemFactory.plainTierName(current), currentCapacity,
                itemFactory.plainTierName(next), nextCapacity, cost, status);
    }

    private void reopenStorage(Player player, BackpackInfoHolder holder) {
        ItemStack reference = findReference(player, holder.backpackId());
        if (reference == null) {
            messages.send(player, "reference-required");
            player.closeInventory();
            return;
        }
        if (!service.open(player, reference)) {
            return;
        }
        service.session(player.getUniqueId()).ifPresent(storage -> {
            int page = Math.min(holder.returnPage(), storage.pageCount() - 1);
            if (page > 0) {
                service.changePage(storage, page);
            }
        });
    }

    private ItemStack summary(BackpackViewModel view) {
        List<Component> lore = new ArrayList<>();
        lore.add(config.component("<gray>Tier: <white><tier></white>", "tier", view.tierName()));
        lore.add(config.component("<gray>Owner: <white><owner></white>", "owner", view.ownerName()));
        lore.add(config.component("<gray>Storage: <white><used>/<capacity></white> slots",
                "used", Integer.toString(view.usedSlots()), "capacity", Integer.toString(view.capacity())));
        lore.add(config.component("<gray>Free: <white><free></white> slots", "free", Integer.toString(view.freeSlots())));
        lore.add(config.component("<gray>Pages: <white><pages></white>", "pages", Integer.toString(view.pageCount())));
        lore.add(Component.empty());
        lore.add(storageState(view));
        if (view.adminOverride()) {
            lore.add(config.component("<gold><bold>ADMIN OVERRIDE</bold></gold> <gray>— owner remains unchanged.</gray>"));
        }
        return item(Material.BOOK, config.component("<gradient:#46d3ff:#8b5cf6><bold>Backpack Info</bold></gradient>"),
                lore, false);
    }

    private Component storageState(BackpackViewModel view) {
        return switch (view.storageState()) {
            case EMPTY -> config.component("<aqua><bold>EMPTY</bold></aqua> <gray>— move items in normally or use Quick Deposit.</gray>");
            case AVAILABLE -> config.component("<green><bold>AVAILABLE</bold></green> <gray>— storage space remains.</gray>");
            case FULL -> config.component("<yellow><bold>FULL</bold></yellow> <gray>— remove an item before adding more.</gray>");
        };
    }

    private ItemStack nextTier(UpgradeViewModel view) {
        if (view.status() == UpgradeViewModel.Status.MAX_TIER) {
            return item(Material.NETHER_STAR, config.component("<aqua><bold>Maximum Tier</bold>"),
                    List.of(config.component("<gray>This backpack cannot be upgraded further.</gray>")), true);
        }
        if (view.nextTier().isBlank()) {
            return item(Material.GRAY_DYE, config.component("<gray><bold>Next Tier Unavailable</bold>"),
                    List.of(statusExplanation(view.status())), false);
        }
        return item(Material.ENDER_CHEST, config.component("<green><bold>Next Tier</bold>"),
                List.of(
                        config.component("<gray>Tier: <white><tier></white>", "tier", view.nextTier()),
                        config.component("<gray>Capacity: <white><slots></white> slots", "slots", Integer.toString(view.nextCapacity())),
                        config.component("<gray>Gain: <green>+<gain></green> slots",
                                "gain", Integer.toString(Math.max(0, view.nextCapacity() - view.currentCapacity())))
                ), false);
    }

    private ItemStack upgradeAction(UpgradeViewModel view) {
        Material material = view.ready() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
        List<Component> lore = new ArrayList<>();
        if (!view.nextTier().isBlank()) {
            lore.add(config.component("<gray><current></gray> <white>→</white> <green><next></green>",
                    "current", view.currentTier(), "next", view.nextTier()));
            lore.add(config.component("<gray>Capacity: <white><current></white> → <green><next></green>",
                    "current", Integer.toString(view.currentCapacity()), "next", Integer.toString(view.nextCapacity())));
            lore.add(config.component("<gray>Cost: <white><cost></white>", "cost", view.formattedCost()));
            lore.add(Component.empty());
        }
        lore.add(statusComponent(view.status()));
        lore.add(Component.empty());
        lore.add(view.ready()
                ? config.component("<yellow>Click</yellow> <gray>to confirm this upgrade.</gray>")
                : statusExplanation(view.status()));
        return item(material,
                config.component(view.ready() ? "<green><bold>Upgrade Backpack</bold>" : "<gray><bold>Upgrade Unavailable</bold>"),
                lore, view.ready());
    }

    private ItemStack restrictions(BackpackViewModel view) {
        List<Component> lore = new ArrayList<>();
        lore.add(config.component("<gray>• Backpacks cannot be stored inside another backpack.</gray>"));
        lore.add(config.component("<gray>• Page, Sort, and Quick Deposit require an empty cursor.</gray>"));
        lore.add(config.component("<gray>• Stored custom item metadata is preserved exactly.</gray>"));
        lore.add(config.component(view.quickDepositEnabled()
                ? "<green>Quick Deposit is enabled.</green>"
                : "<yellow>Quick Deposit is disabled.</yellow>"));
        return item(Material.SHIELD, config.component("<aqua><bold>Storage Rules</bold>"), lore, false);
    }

    private Component statusComponent(UpgradeViewModel.Status status) {
        return switch (status) {
            case READY -> config.component("<green><bold>Status: READY</bold></green>");
            case MAX_TIER -> config.component("<aqua><bold>Status: MAX TIER</bold></aqua>");
            case UPGRADES_DISABLED -> config.component("<yellow><bold>Status: UPGRADES DISABLED</bold></yellow>");
            case WRONG_OWNER -> config.component("<red><bold>Status: NOT YOUR BACKPACK</bold></red>");
            case MISSING_REQUIREMENT -> config.component("<yellow><bold>Status: MISSING REQUIREMENT</bold></yellow>");
            case ECONOMY_UNAVAILABLE -> config.component("<red><bold>Status: ECONOMY UNAVAILABLE</bold></red>");
            case INSUFFICIENT_FUNDS -> config.component("<yellow><bold>Status: INSUFFICIENT FUNDS</bold></yellow>");
            case BACKPACK_OPEN -> config.component("<yellow><bold>Status: CLOSE STORAGE FIRST</bold></yellow>");
            case REFERENCE_MISSING -> config.component("<yellow><bold>Status: BACKPACK ITEM REQUIRED</bold></yellow>");
        };
    }

    private Component statusExplanation(UpgradeViewModel.Status status) {
        return switch (status) {
            case READY -> config.component("<gray>All upgrade requirements are met.</gray>");
            case MAX_TIER -> config.component("<gray>This backpack is already fully upgraded.</gray>");
            case UPGRADES_DISABLED -> config.component("<gray>Backpack upgrades are disabled on this server.</gray>");
            case WRONG_OWNER -> config.component("<gray>Only the owner or an authorized administrator can upgrade it.</gray>");
            case MISSING_REQUIREMENT -> config.component("<gray>You do not meet the upgrade requirement yet.</gray>");
            case ECONOMY_UNAVAILABLE -> config.component("<gray>Paid upgrades are temporarily unavailable.</gray>");
            case INSUFFICIENT_FUNDS -> config.component("<gray>You do not have enough money for this upgrade.</gray>");
            case BACKPACK_OPEN -> config.component("<gray>Close the storage session before upgrading.</gray>");
            case REFERENCE_MISSING -> config.component("<gray>Keep this backpack item in your inventory to upgrade it.</gray>");
        };
    }

    private ItemStack findReference(Player player, UUID backpackId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemFactory.backpackId(item).filter(backpackId::equals).isPresent()) {
                return item;
            }
        }
        return null;
    }

    private static int usedSlots(ItemStack[] contents, int capacity) {
        int used = 0;
        for (int slot = 0; slot < Math.min(capacity, contents.length); slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir()) {
                used++;
            }
        }
        return used;
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }
}
