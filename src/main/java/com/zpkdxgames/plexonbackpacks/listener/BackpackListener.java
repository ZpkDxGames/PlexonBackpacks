package com.zpkdxgames.plexonbackpacks.listener;

import com.zpkdxgames.plexonbackpacks.inventory.BackpackHolder;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackLayout;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.item.BackpackNestingPolicy;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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

public final class BackpackListener implements Listener {
    private final BackpackService service;
    private final BackpackItemFactory itemFactory;
    private final Messages messages;

    public BackpackListener(BackpackService service, BackpackItemFactory itemFactory, Messages messages) {
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

        int rawSlot = event.getRawSlot();
        boolean clickedTop = rawSlot >= 0 && rawSlot < top.getSize();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isActiveBackpack(holder, current) || isActiveBackpack(holder, cursor)) {
            event.setCancelled(true);
            return;
        }

        if (clickedTop && !BackpackLayout.isStorageGuiSlot(holder.capacity(), holder.page(), rawSlot)) {
            event.setCancelled(true);
            handleControl(event, holder, rawSlot);
            return;
        }

        if (clickedTop && (event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.DOUBLE_CLICK)) {
            event.setCancelled(true);
            messages.send(event.getWhoClicked(), "protected-interaction");
            return;
        }

        if (clickedTop && BackpackNestingPolicy.containsBackpack(cursor, itemFactory)) {
            cancelNesting(event);
            return;
        }
        if (clickedTop && BackpackNestingPolicy.containsBackpack(current, itemFactory)) {
            cancelNesting(event);
            return;
        }
        if (!clickedTop
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && BackpackNestingPolicy.containsBackpack(current, itemFactory)) {
            cancelNesting(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BackpackHolder holder)) {
            return;
        }
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < top.getSize());
        if (!touchesTop) {
            return;
        }
        boolean touchesProtected = event.getRawSlots().stream()
                .filter(slot -> slot >= 0 && slot < top.getSize())
                .anyMatch(slot -> !BackpackLayout.isStorageGuiSlot(holder.capacity(), holder.page(), slot));
        if (touchesProtected || isActiveBackpack(holder, event.getOldCursor())
                || BackpackNestingPolicy.containsBackpack(event.getOldCursor(), itemFactory)) {
            event.setCancelled(true);
            if (BackpackNestingPolicy.containsBackpack(event.getOldCursor(), itemFactory)) {
                messages.send(event.getWhoClicked(), "cannot-nest");
            }
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (service.session(player.getUniqueId()).isPresent()) {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (service.session(event.getPlayer().getUniqueId()).isPresent()) {
            event.getPlayer().closeInventory();
        }
    }

    private void handleControl(InventoryClickEvent event, BackpackHolder holder, int rawSlot) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int relative = rawSlot - holder.controlRowStart();
        switch (relative) {
            case BackpackLayout.PREVIOUS_SLOT_OFFSET -> service.changePage(holder, holder.page() - 1);
            case BackpackLayout.SORT_SLOT_OFFSET -> {
                if (!service.sort(holder)) {
                    messages.send(player, "operation-failed");
                }
            }
            case BackpackLayout.QUICK_DEPOSIT_SLOT_OFFSET -> {
                int moved = service.quickDeposit(player, holder);
                messages.send(player, moved > 0 ? "quick-deposit" : "quick-deposit-none",
                        "amount", Integer.toString(moved));
            }
            case BackpackLayout.CLOSE_SLOT_OFFSET -> player.closeInventory();
            case BackpackLayout.NEXT_SLOT_OFFSET -> service.changePage(holder, holder.page() + 1);
            default -> {
                // Information/filler slots are intentionally inert.
            }
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
