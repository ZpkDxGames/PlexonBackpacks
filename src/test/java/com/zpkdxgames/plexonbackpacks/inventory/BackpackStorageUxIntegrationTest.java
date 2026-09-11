package com.zpkdxgames.plexonbackpacks.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.api.PlexonBackpacksAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class BackpackStorageUxIntegrationTest {
    private ServerMock server;
    private PlexonBackpacksAPI api;
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PlexonBackpacksPlugin plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        api = Bukkit.getServicesManager().load(PlexonBackpacksAPI.class);
        assertNotNull(api);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void basicBackpackUsesAllNineStorageSlotsAndHumanReadableInfo() {
        Player player = server.addPlayer();
        ItemStack backpack = api.create("basic");
        player.getInventory().setItemInMainHand(backpack);

        assertTrue(api.open(player, backpack));
        BackpackHolder holder = (BackpackHolder) player.getOpenInventory().getTopInventory().getHolder();

        assertEquals(9, holder.capacity());
        assertEquals(9, holder.visibleStorageSlots());
        assertEquals(18, holder.getInventory().getSize());
        ItemStack info = holder.getInventory().getItem(holder.controlRowStart() + BackpackLayout.INFO_SLOT_OFFSET);
        String lore = plainLore(info);
        assertTrue(lore.contains("Tier:"));
        assertTrue(lore.contains("Storage: 0/9"));
        assertTrue(lore.contains("Free: 9"));
        assertTrue(lore.contains("EMPTY"));
        assertFalse(lore.contains("UUID"));
        assertFalse(lore.contains("session"));
    }

    @Test
    void netheritePreservesFiftyFourStorageSlotsAcrossTwoPages() {
        Player player = server.addPlayer();
        ItemStack backpack = api.create("netherite");
        player.getInventory().setItemInMainHand(backpack);

        assertTrue(api.open(player, backpack));
        BackpackHolder holder = (BackpackHolder) player.getOpenInventory().getTopInventory().getHolder();

        assertEquals(54, holder.capacity());
        assertEquals(2, holder.pageCount());
        assertEquals(45, holder.visibleStorageSlots());
        assertEquals(54, holder.getInventory().getSize());
        ItemStack next = holder.getInventory().getItem(holder.controlRowStart() + BackpackLayout.NEXT_SLOT_OFFSET);
        assertNotNull(next);
        assertEquals(Material.ARROW, next.getType());
    }

    private String plainLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().lore() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Component line : item.getItemMeta().lore()) {
            builder.append(plain.serialize(line)).append('\n');
        }
        return builder.toString();
    }
}
