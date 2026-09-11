package com.zpkdxgames.plexonbackpacks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.integration.economy.EconomyGateway;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackHolder;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.storage.BackpackDataStore;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class BackpackServiceSafetyIntegrationTest {
    private ServerMock server;
    private PlexonBackpacksPlugin plugin;
    private ConfigManager config;
    private BackpackItemFactory itemFactory;
    private BackpackDataStore store;
    private BackpackService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        Bukkit.getPluginManager().disablePlugin(plugin);
        config = new ConfigManager(plugin);
        config.reload();
        itemFactory = new BackpackItemFactory(plugin, config);
        store = new BackpackDataStore(plugin, config);
        store.load();
        service = new BackpackService(plugin, config, new Messages(plugin), itemFactory, store, new FreeEconomy());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void trailingNullLegacyArrayNeverBecomesQuickDepositCapacity() {
        TierDefinition basic = config.tier("basic").orElseThrow();
        UUID id = UUID.randomUUID();
        ItemStack reference = itemFactory.create(basic, id, null);
        BackpackRecord record = store.register(id, basic.id(), null, basic.slots());
        ItemStack[] legacy = new ItemStack[54];
        for (int slot = 0; slot < 9; slot++) {
            legacy[slot] = new ItemStack(Material.DIAMOND_SWORD);
        }
        record.contents(legacy);
        store.markDirty(id);
        assertTrue(store.saveSync());

        Player player = server.addPlayer();
        player.getInventory().setItemInMainHand(reference);
        player.getInventory().setItem(1, new ItemStack(Material.STONE, 64));
        assertTrue(service.open(player, reference));
        BackpackHolder holder = service.session(player.getUniqueId()).orElseThrow();

        assertEquals(9, holder.capacity());
        assertEquals(9, record.contents().length);
        assertEquals(0, service.quickDeposit(player, holder));
        assertEquals(64, player.getInventory().getItem(1).getAmount());
        assertEquals(9, record.contents().length);
    }

    @Test
    void cursorBlocksEveryRerenderingStorageControlWithoutChangingExactCursor() {
        TierDefinition netherite = config.tier("netherite").orElseThrow();
        ItemStack reference = service.createBackpack(netherite);
        Player player = server.addPlayer();
        player.getInventory().setItemInMainHand(reference);
        assertTrue(service.open(player, reference));
        BackpackHolder holder = service.session(player.getUniqueId()).orElseThrow();

        ItemStack cursor = new ItemStack(Material.DIAMOND, 7);
        NamespacedKey custom = new NamespacedKey("cursor-test", "identity");
        var meta = cursor.getItemMeta();
        meta.getPersistentDataContainer().set(custom, PersistentDataType.STRING, "exact-cursor");
        cursor.setItemMeta(meta);
        ItemStack expected = cursor.clone();
        player.setItemOnCursor(cursor);
        player.getInventory().setItem(1, new ItemStack(Material.STONE, 32));

        assertFalse(service.changePage(holder, 1));
        assertEquals(0, holder.page());
        assertEquals(expected, player.getItemOnCursor());
        assertFalse(service.sort(holder));
        assertEquals(expected, player.getItemOnCursor());
        assertEquals(0, service.quickDeposit(player, holder));
        assertEquals(expected, player.getItemOnCursor());
        assertEquals("exact-cursor", player.getItemOnCursor().getItemMeta().getPersistentDataContainer()
                .get(custom, PersistentDataType.STRING));
    }

    @Test
    void directLegacyNestedItemCanRemainThenBeExtractedWithoutTrappingSession() {
        TierDefinition basic = config.tier("basic").orElseThrow();
        ItemStack inner = itemFactory.create(basic, null);
        UUID outerId = UUID.randomUUID();
        ItemStack outerReference = itemFactory.create(basic, outerId, null);
        BackpackRecord outer = store.register(outerId, basic.id(), null, basic.slots());
        ItemStack[] contents = new ItemStack[9];
        contents[0] = inner.clone();
        outer.contents(contents);
        store.markDirty(outerId);
        assertTrue(store.saveSync());

        Player player = server.addPlayer();
        player.getInventory().setItemInMainHand(outerReference);
        assertTrue(service.open(player, outerReference));
        BackpackHolder holder = service.session(player.getUniqueId()).orElseThrow();
        assertTrue(service.close(holder, holder.getInventory()));

        assertTrue(service.open(player, outerReference));
        holder = service.session(player.getUniqueId()).orElseThrow();
        ItemStack extracted = holder.getInventory().getItem(0).clone();
        holder.getInventory().setItem(0, null);
        player.getInventory().setItem(1, extracted);
        assertTrue(service.close(holder, holder.getInventory()));
        assertNull(outer.contents()[0]);
        assertEquals(inner, player.getInventory().getItem(1));
    }

    @Test
    void containerMediatedLegacyNestedItemCanBeExtractedWithoutMetadataLoss() {
        TierDefinition basic = config.tier("basic").orElseThrow();
        ItemStack inner = itemFactory.create(basic, null);
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta stateMeta = (BlockStateMeta) shulker.getItemMeta();
        Container container = (Container) stateMeta.getBlockState();
        container.getInventory().setItem(0, inner);
        stateMeta.setBlockState(container);
        shulker.setItemMeta(stateMeta);

        UUID outerId = UUID.randomUUID();
        ItemStack outerReference = itemFactory.create(basic, outerId, null);
        BackpackRecord outer = store.register(outerId, basic.id(), null, basic.slots());
        ItemStack[] contents = new ItemStack[9];
        contents[0] = shulker.clone();
        outer.contents(contents);
        store.markDirty(outerId);
        assertTrue(store.saveSync());

        Player player = server.addPlayer();
        player.getInventory().setItemInMainHand(outerReference);
        assertTrue(service.open(player, outerReference));
        BackpackHolder holder = service.session(player.getUniqueId()).orElseThrow();
        ItemStack extracted = holder.getInventory().getItem(0).clone();
        holder.getInventory().setItem(0, null);
        player.getInventory().setItem(1, extracted);
        assertTrue(service.close(holder, holder.getInventory()));

        assertNull(outer.contents()[0]);
        assertEquals(shulker, player.getInventory().getItem(1));
    }

    private static final class FreeEconomy implements EconomyGateway {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String providerName() {
            return "TestEconomy";
        }

        @Override
        public boolean has(UUID playerId, double amount) {
            return true;
        }

        @Override
        public boolean withdraw(UUID playerId, double amount) {
            return true;
        }

        @Override
        public boolean refund(UUID playerId, double amount) {
            return true;
        }
    }
}
