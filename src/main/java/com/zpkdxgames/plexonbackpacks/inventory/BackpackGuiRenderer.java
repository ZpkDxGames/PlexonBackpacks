package com.zpkdxgames.plexonbackpacks.inventory;

import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
                config.component("<dark_gray>Backpack <gray>•</gray> <white><tier></white> <gray>[<page>/<pages>]",
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

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = visible; slot < holder.controlRowStart(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
        for (int slot = holder.controlRowStart(); slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }

        int controls = holder.controlRowStart();
        inventory.setItem(controls + BackpackLayout.INFO_SLOT_OFFSET, information(record, tier, holder));
        inventory.setItem(controls + BackpackLayout.PREVIOUS_SLOT_OFFSET,
                holder.page() > 0
                        ? item(Material.ARROW, config.component("<aqua><bold>Previous Page</bold>"),
                                List.of(config.component("<gray>Page <white><page></white> of <white><pages></white>",
                                        "page", Integer.toString(holder.page()),
                                        "pages", Integer.toString(holder.pageCount()))))
                        : filler.clone());
        inventory.setItem(controls + BackpackLayout.SORT_SLOT_OFFSET,
                item(Material.HOPPER, config.component("<yellow><bold>Sort</bold>"),
                        List.of(config.component("<gray>Sort stored items by material and name"),
                                config.component("<dark_gray>Exact ItemStacks are moved, never rebuilt"))));
        inventory.setItem(controls + BackpackLayout.QUICK_DEPOSIT_SLOT_OFFSET,
                item(Material.CHEST, config.component("<green><bold>Quick Deposit</bold>"),
                        List.of(config.component("<gray>Move eligible inventory items into this backpack"),
                                config.component("<dark_gray>Backpacks and this active reference are excluded"))));
        inventory.setItem(controls + BackpackLayout.CLOSE_SLOT_OFFSET,
                item(Material.BARRIER, config.component("<red><bold>Close</bold>"),
                        List.of(config.component("<gray>Commit this session and close safely"))));
        inventory.setItem(controls + BackpackLayout.NEXT_SLOT_OFFSET,
                holder.page() + 1 < holder.pageCount()
                        ? item(Material.ARROW, config.component("<aqua><bold>Next Page</bold>"),
                                List.of(config.component("<gray>Page <white><page></white> of <white><pages></white>",
                                        "page", Integer.toString(holder.page() + 2),
                                        "pages", Integer.toString(holder.pageCount()))))
                        : filler.clone());
        holder.resetSnapshotHash();
    }

    private ItemStack information(BackpackRecord record, TierDefinition tier, BackpackHolder holder) {
        UUID owner = record.owner();
        return item(Material.BOOK,
                config.component("<gradient:#46d3ff:#8b5cf6><bold>Backpack Status</bold></gradient>"),
                List.of(
                        config.component("<gray>Tier: <white><tier></white>",
                                "tier", itemFactory.plainTierName(tier)),
                        config.component("<gray>Capacity: <white><capacity></white> slots",
                                "capacity", Integer.toString(holder.capacity())),
                        config.component("<gray>Owner: <white><owner></white>",
                                "owner", itemFactory.ownerName(owner)),
                        config.component("<gray>Page: <white><page>/<pages></white>",
                                "page", Integer.toString(holder.page() + 1),
                                "pages", Integer.toString(holder.pageCount())),
                        Component.empty(),
                        config.component("<dark_gray>UUID: <uuid>", "uuid", record.id().toString())
                ));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
