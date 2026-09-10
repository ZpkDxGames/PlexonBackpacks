package com.zpkdxgames.plexonbackpacks.listener;

import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.inventory.AdminMenuHolder;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class AdminMenuListener implements Listener {
    private final ConfigManager config;
    private final Messages messages;
    private final BackpackItemFactory itemFactory;
    private final BackpackService backpackService;

    public AdminMenuListener(
            ConfigManager config,
            Messages messages,
            BackpackItemFactory itemFactory,
            BackpackService backpackService
    ) {
        this.config = config;
        this.messages = messages;
        this.itemFactory = itemFactory;
        this.backpackService = backpackService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof AdminMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) {
            return;
        }
        if (!player.hasPermission("plexonbackpacks.admin-gui")) {
            player.closeInventory();
            messages.send(player, "no-permission");
            return;
        }

        int slot = event.getRawSlot();
        if (slot == AdminMenuHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == AdminMenuHolder.AUTHOR_SLOT) {
            messages.send(player, "author-link");
            return;
        }

        String tierId = holder.tierAt(slot);
        if (tierId == null) {
            return;
        }
        TierDefinition tier = config.tier(tierId).orElse(null);
        if (tier == null) {
            messages.send(player, "invalid-tier", "tier", tierId);
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            messages.send(player, "inventory-space", "player", player.getName(), "amount", "1");
            return;
        }

        ItemStack backpack;
        try {
            backpack = backpackService.createBackpack(tier);
        } catch (RuntimeException exception) {
            messages.send(player, "persistence-failed");
            return;
        }
        if (!player.getInventory().addItem(backpack).isEmpty()) {
            messages.send(player, "operation-failed");
            return;
        }
        messages.send(player, "admin-gui-given", "tier", itemFactory.plainTierName(tier));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof AdminMenuHolder) {
            event.setCancelled(true);
        }
    }
}
