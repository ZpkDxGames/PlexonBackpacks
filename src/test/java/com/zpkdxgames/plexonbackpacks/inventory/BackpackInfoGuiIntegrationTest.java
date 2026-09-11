package com.zpkdxgames.plexonbackpacks.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.integration.economy.EconomyGateway;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import com.zpkdxgames.plexonbackpacks.storage.BackpackDataStore;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class BackpackInfoGuiIntegrationTest {
    private ServerMock server;
    private ConfigManager config;
    private BackpackItemFactory itemFactory;
    private BackpackDataStore store;
    private FakeEconomy economy;
    private BackpackService service;
    private BackpackInfoGui gui;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PlexonBackpacksPlugin plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        config = new ConfigManager(plugin);
        config.reload();
        itemFactory = new BackpackItemFactory(plugin, config);
        store = new BackpackDataStore(plugin, config);
        store.load();
        economy = new FakeEconomy(100_000.0D);
        service = new BackpackService(plugin, config, new Messages(plugin), itemFactory, store, economy);
        gui = new BackpackInfoGui(plugin, config, new Messages(plugin), itemFactory, service, economy);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openingUpgradePreviewDoesNotMutateTierInventoryOrEconomy() {
        Player player = server.addPlayer();
        TierDefinition basic = config.tier("basic").orElseThrow();
        ItemStack reference = service.createBackpack(basic);
        UUID id = itemFactory.backpackId(reference).orElseThrow();
        player.getInventory().setItemInMainHand(reference);

        gui.openForReference(player, reference);

        assertInstanceOf(BackpackInfoHolder.class, player.getOpenInventory().getTopInventory().getHolder());
        assertEquals("basic", store.find(id).orElseThrow().tierId());
        assertEquals("basic", itemFactory.tierId(player.getInventory().getItemInMainHand()).orElseThrow());
        assertEquals(0, economy.withdrawCalls);
        assertEquals(100_000.0D, economy.balance);
    }

    @Test
    void repeatedSubmissionFromSamePreviewCanChargeAtMostOnce() {
        Player player = server.addPlayer();
        TierDefinition basic = config.tier("basic").orElseThrow();
        ItemStack reference = service.createBackpack(basic);
        UUID id = itemFactory.backpackId(reference).orElseThrow();
        player.getInventory().setItemInMainHand(reference);
        gui.openForReference(player, reference);
        BackpackInfoHolder holder = (BackpackInfoHolder) player.getOpenInventory().getTopInventory().getHolder();

        gui.handleClick(player, holder, BackpackInfoGui.UPGRADE_SLOT);
        gui.handleClick(player, holder, BackpackInfoGui.UPGRADE_SLOT);

        assertEquals("iron", store.find(id).orElseThrow().tierId());
        assertEquals(1, economy.withdrawCalls);
        assertTrue(economy.balance < 100_000.0D);
    }

    private static final class FakeEconomy implements EconomyGateway {
        private double balance;
        private int withdrawCalls;

        private FakeEconomy(double balance) {
            this.balance = balance;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String providerName() {
            return "FakeEconomy";
        }

        @Override
        public boolean has(UUID playerId, double amount) {
            return balance >= amount;
        }

        @Override
        public boolean withdraw(UUID playerId, double amount) {
            if (balance < amount) {
                return false;
            }
            balance -= amount;
            withdrawCalls++;
            return true;
        }

        @Override
        public boolean refund(UUID playerId, double amount) {
            balance += amount;
            return true;
        }
    }
}
