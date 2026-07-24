package com.zpkdxgames.plexonbackpacks.listener;

import com.zpkdxgames.plexonbackpacks.inventory.BackpackHolder;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class BackpackListener implements Listener {
    private final BackpackService service;
    private final BackpackItemFactory itemFactory;
    private final Messages messages;

    public BackpackListener(
            BackpackService service,
            BackpackItemFactory itemFactory,
            Messages messages
    ) {
        this.service = service;
        this.itemFactory = itemFactory;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        if (event.useItemInHand() == Result.DENY && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack item = event.getItem();
        if (!itemFactory.isBackpack(item)) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND
                && itemFactory.isBackpack(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        service.open(event.getPlayer(), item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!itemFactory.isBackpack(event.getItemInHand())) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "cannot-place");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (itemFactory.isBackpack(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof BackpackHolder holder) {
            service.close(holder, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BackpackHolder holder)) {
            return;
        }

        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isActiveBackpack(holder, current) || isActiveBackpack(holder, cursor)) {
            event.setCancelled(true);
            return;
        }

        if (clickedTop && itemFactory.isBackpack(cursor)) {
            cancelNesting(event);
            return;
        }
        if (!clickedTop
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && itemFactory.isBackpack(current)) {
            cancelNesting(event);
            return;
        }
        if (clickedTop && event.getClick() == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            if (button >= 0 && itemFactory.isBackpack(event.getWhoClicked().getInventory().getItem(button))) {
                cancelNesting(event);
                return;
            }
        }
        if (clickedTop
                && event.getClick() == ClickType.SWAP_OFFHAND
                && itemFactory.isBackpack(event.getWhoClicked().getInventory().getItemInOffHand())) {
            cancelNesting(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BackpackHolder holder)) {
            return;
        }

        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        if (!touchesTop) {
            return;
        }
        if (isActiveBackpack(holder, event.getOldCursor()) || itemFactory.isBackpack(event.getOldCursor())) {
            event.setCancelled(true);
            messages.send(event.getWhoClicked(), "cannot-nest");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        service.session(event.getPlayer().getUniqueId()).ifPresent(holder -> {
            if (isActiveBackpack(holder, event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        service.session(event.getPlayer().getUniqueId()).ifPresent(holder -> {
            if (isActiveBackpack(holder, event.getMainHandItem())
                    || isActiveBackpack(holder, event.getOffHandItem())) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (service.session(event.getPlayer().getUniqueId()).isPresent()) {
            event.getPlayer().closeInventory();
        }
    }

    private boolean isActiveBackpack(BackpackHolder holder, ItemStack item) {
        Optional<UUID> id = itemFactory.backpackId(item);
        return id.filter(holder.backpackId()::equals).isPresent();
    }

    private void cancelNesting(InventoryClickEvent event) {
        event.setCancelled(true);
        messages.send(event.getWhoClicked(), "cannot-nest");
    }
}
