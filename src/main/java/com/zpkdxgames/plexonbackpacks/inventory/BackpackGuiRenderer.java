package com.zpkdxgames.plexonbackpacks.inventory;

import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Storage-first renderer. Authoritative item slots remain the live backpack session. */
public final class BackpackGuiRenderer {
    private final ConfigManager config;
    private final BackpackItemFactory itemFactory;

    public BackpackGuiRenderer(ConfigManager config, BackpackItemFactory itemFactory) {
        this.config = config;
        this.itemFactory = itemFactory;
    }

    public Inventory create(BackpackHolder holder, BackpackRecord record, TierDefinition tier) {
        Inventory inventory = Bukkit.createInventory(
                holder,
                BackpackLayout.inventorySize(holder.capacity()),
                config.component("<dark_gray><tier> <gray>•</gray> <white>Page <page>/<pages></white>",
                        "tier", itemFactory.plainTierName(tier),
                        "page", Integer.toString(holder.page() + 1),
                        "pages", Integer.toString(holder.pageCount()))
        );
        holder.inventory(inventory);
        render(holder, record, tier);
        return inventory;
    }

    public void render(BackpackHolder holder, BackpackRecord record, TierDefinition tier) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        ItemStack[] contents = record.contents();
        int pageStart = BackpackLayout.pageStart(holder.page());
        int visible = holder.visibleStorageSlots();
        for (int guiSlot = 0; guiSlot < visible; guiSlot++) {
            int recordSlot = pageStart + guiSlot;
            if (recordSlot < contents.length && contents[recordSlot] != null) {
                inventory.setItem(guiSlot, contents[recordSlot].clone());
            }
        }

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of(), false);
        for (int slot = visible; slot < holder.controlRowStart(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
        for (int slot = holder.controlRowStart(); slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }

        BackpackViewModel view = view(record, tier, holder);
        int controls = holder.controlRowStart();
        inventory.setItem(controls + BackpackLayout.INFO_SLOT_OFFSET, information(view));
        inventory.setItem(controls + BackpackLayout.PREVIOUS_SLOT_OFFSET,
                holder.page() > 0
                        ? item(Material.ARROW, config.component("<aqua><bold>Previous</bold>"),
                                List.of(config.component("<gray>Return to page <white><page></white>.",
                                        "page", Integer.toString(holder.page()))), false)
                        : filler.clone());
        inventory.setItem(controls + BackpackLayout.SORT_SLOT_OFFSET,
                item(Material.HOPPER, config.component("<yellow><bold>Sort Backpack</bold>"),
                        List.of(
                                config.component("<gray>Current: <white>Material → Amount</white>"),
                                config.component("<gray>Reorders stored items once across all pages.</gray>"),
                                config.component("<gray>Custom item data is preserved.</gray>"),
                                Component.empty(),
                                config.component("<yellow>Click</yellow> <gray>to sort.</gray>")), false));
        inventory.setItem(controls + BackpackLayout.QUICK_DEPOSIT_SLOT_OFFSET,
                quickDeposit(view));
        inventory.setItem(controls + BackpackLayout.CLOSE_SLOT_OFFSET,
                item(Material.BARRIER, config.component("<red><bold>Close</bold>"),
                        List.of(config.component("<gray>Save this session and close safely.</gray>")), false));
        inventory.setItem(controls + BackpackLayout.NEXT_SLOT_OFFSET,
                holder.page() + 1 < holder.pageCount()
                        ? item(Material.ARROW, config.component("<aqua><bold>Next</bold>"),
                                List.of(config.component("<gray>Continue to page <white><page></white>.",
                                        "page", Integer.toString(holder.page() + 2))), false)
                        : filler.clone());
        holder.resetSnapshotHash();
    }

    private BackpackViewModel view(BackpackRecord record, TierDefinition tier, BackpackHolder holder) {
        ItemStack[] contents = record.contents();
        int used = 0;
        for (int slot = 0; slot < Math.min(holder.capacity(), contents.length); slot++) {
            ItemStack stack = contents[slot];
            if (stack != null && !stack.getType().isAir()) {
                used++;
            }
        }
        boolean adminOverride = record.owner() != null && !record.owner().equals(holder.viewerId());
        return new BackpackViewModel(
                itemFactory.plainTierName(tier),
                itemFactory.ownerName(record.owner()),
                holder.capacity(),
                used,
                holder.page(),
                holder.pageCount(),
                adminOverride,
                config.quickDepositEnabled());
    }

    private ItemStack information(BackpackViewModel view) {
        List<Component> lore = new ArrayList<>();
        lore.add(config.component("<gray>Tier: <white><tier></white>", "tier", view.tierName()));
        lore.add(config.component("<gray>Owner: <white><owner></white>", "owner", view.ownerName()));
        lore.add(config.component("<gray>Storage: <white><used>/<capacity></white> slots",
                "used", Integer.toString(view.usedSlots()),
                "capacity", Integer.toString(view.capacity())));
        lore.add(config.component("<gray>Free: <white><free></white> slots", "free", Integer.toString(view.freeSlots())));
        lore.add(config.component("<gray>Page: <white><page>/<pages></white>",
                "page", Integer.toString(view.page() + 1), "pages", Integer.toString(view.pageCount())));
        lore.add(Component.empty());
        lore.add(storageState(view));
        if (view.adminOverride()) {
            lore.add(config.component("<gold><bold>ADMIN OVERRIDE</bold></gold> <gray>— you are not the owner.</gray>"));
        }
        lore.add(Component.empty());
        lore.add(config.component("<yellow>Click</yellow> <gray>for backpack info and upgrades.</gray>"));
        return item(Material.BOOK,
                config.component("<gradient:#46d3ff:#8b5cf6><bold>Backpack Info</bold></gradient>"), lore, false);
    }

    private ItemStack quickDeposit(BackpackViewModel view) {
        if (!view.quickDepositEnabled()) {
            return item(Material.GRAY_DYE, config.component("<gray><bold>Quick Deposit</bold>"),
                    List.of(config.component("<yellow>Status: DISABLED</yellow>")), false);
        }
        return item(Material.CHEST, config.component("<green><bold>Quick Deposit</bold>"),
                List.of(
                        config.component("<gray>Moves all eligible items from your inventory</gray>"),
                        config.component("<gray>into available backpack space.</gray>"),
                        config.component("<gray>Backpacks themselves are never deposited.</gray>"),
                        config.component("<gray>Empty backpack slots: <white><free></white>",
                                "free", Integer.toString(view.freeSlots())),
                        Component.empty(),
                        config.component("<yellow>Click</yellow> <gray>to deposit once.</gray>")), false);
    }

    private Component storageState(BackpackViewModel view) {
        return switch (view.storageState()) {
            case EMPTY -> config.component("<aqua><bold>EMPTY</bold></aqua> <gray>— add items normally or use Quick Deposit.</gray>");
            case AVAILABLE -> config.component("<green><bold>AVAILABLE</bold></green> <gray>— storage space remains.</gray>");
            case FULL -> config.component("<yellow><bold>FULL</bold></yellow> <gray>— remove an item before adding more.</gray>");
        };
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
