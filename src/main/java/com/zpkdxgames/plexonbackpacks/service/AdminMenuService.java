package com.zpkdxgames.plexonbackpacks.service;

import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.inventory.AdminMenuHolder;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminMenuService {
    private static final UUID AUTHOR_UUID = UUID.fromString("78115eea-fa52-4cd0-895d-f1d8c5a3daf6");
    private static final int[] TIER_SLOTS = {9, 11, 13, 15, 17};

    private final ConfigManager config;
    private final BackpackItemFactory itemFactory;
    private final Map<String, ItemStack> tierTemplates = new LinkedHashMap<>();
    private ItemStack filler;
    private ItemStack information;
    private ItemStack author;
    private ItemStack close;

    public AdminMenuService(ConfigManager config, BackpackItemFactory itemFactory) {
        this.config = config;
        this.itemFactory = itemFactory;
        reload();
    }

    public void reload() {
        tierTemplates.clear();
        for (TierDefinition tier : config.tiers()) {
            List<Component> lore = List.of(
                    config.component("<gray>Capacity: <white><slots> slots",
                            "slots", Integer.toString(tier.slots())),
                    config.component("<gray>Recipe: <white><recipe>",
                            "recipe", tier.recipe().enabled() ? "Enabled" : "Disabled"),
                    Component.empty(),
                    config.component("<yellow>Click to receive one"),
                    config.component("<dark_gray>Unbound until its first opening")
            );
            tierTemplates.put(tier.id(), itemFactory.createMenuIcon(tier, lore));
        }

        filler = simpleItem(
                Material.GRAY_STAINED_GLASS_PANE,
                Component.empty(),
                List.of()
        );
        information = simpleItem(
                Material.BOOK,
                config.component("<gradient:#46d3ff:#8b5cf6><bold>PlexonBackpacks</bold></gradient>"),
                List.of(
                        config.component("<gray>Lightweight tiered storage"),
                        config.component("<gray>for Paper 26.2 servers."),
                        Component.empty(),
                        config.component("<aqua>Click a backpack below"),
                        config.component("<aqua>to receive an unbound copy.")
                )
        );
        close = simpleItem(
                Material.BARRIER,
                config.component("<red><bold>Close"),
                List.of(config.component("<gray>Close this menu"))
        );
    }

    public void open(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder();
        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                config.component("<dark_gray>PlexonBackpacks Admin")
        );
        holder.inventory(inventory);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
        inventory.setItem(4, information.clone());
        inventory.setItem(AdminMenuHolder.AUTHOR_SLOT, authorTemplate().clone());
        inventory.setItem(AdminMenuHolder.CLOSE_SLOT, close.clone());

        int index = 0;
        for (Map.Entry<String, ItemStack> entry : tierTemplates.entrySet()) {
            if (index >= TIER_SLOTS.length) {
                break;
            }
            int slot = TIER_SLOTS[index++];
            holder.bindTier(slot, entry.getKey());
            inventory.setItem(slot, entry.getValue().clone());
        }
        player.openInventory(inventory);
    }

    private ItemStack authorTemplate() {
        if (author == null) {
            author = itemFactory.createPlayerProfileHead(
                    AUTHOR_UUID,
                    "ZpkDxGames",
                    config.component("<gradient:#f59e0b:#8b5cf6><bold>Author: ZpkDxGames</bold></gradient>"),
                    List.of(
                            config.component("<gray>Creator of PlexonBackpacks,"),
                            config.component("<gray>PlexonChats, and GhostBlocks."),
                            Component.empty(),
                            config.component("<light_purple>A tiny easter egg ✦"),
                            config.component("<yellow>Click to view the NameMC profile")
                    )
            );
        }
        return author;
    }

    private static ItemStack simpleItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
