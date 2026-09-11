package com.zpkdxgames.plexonbackpacks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.integration.economy.EconomyGateway;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.storage.BackpackDataStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class UpgradeIntegrationTest {
    private ServerMock server;
    private PlexonBackpacksPlugin plugin;
    private ConfigManager config;
    private BackpackItemFactory itemFactory;
    private BackpackDataStore store;
    private FakeEconomy economy;
    private BackpackService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        config = new ConfigManager(plugin);
        config.reload();
        itemFactory = new BackpackItemFactory(plugin, config);
        store = new BackpackDataStore(plugin, config);
        store.load();
        economy = new FakeEconomy(100_000.0D);
        service = new BackpackService(plugin, config, new Messages(plugin), itemFactory, store, economy);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void upgradeChargesExactConfiguredCostAndCommitsTier() {
        Player player = server.addPlayer();
        ItemStack reference = createAndHold(player, "basic");
        UUID id = itemFactory.backpackId(reference).orElseThrow();
        double before = economy.balance;

        assertTrue(service.upgrade(player, reference));
        assertEquals("iron", store.find(id).orElseThrow().tierId());
        assertEquals(before - config.tier("iron").orElseThrow().upgradeCost(), economy.balance);
        assertEquals("iron", itemFactory.tierId(player.getInventory().getItemInMainHand()).orElseThrow());
    }

    @Test
    void insufficientFundsDoNotMutateState() {
        economy.balance = 100.0D;
        Player player = server.addPlayer();
        ItemStack reference = createAndHold(player, "basic");
        UUID id = itemFactory.backpackId(reference).orElseThrow();

        assertFalse(service.upgrade(player, reference));
        assertEquals("basic", store.find(id).orElseThrow().tierId());
        assertEquals(100.0D, economy.balance);
        assertEquals(0, economy.withdrawCalls);
    }

    @Test
    void maximumTierIsBoundedAndNeverCharged() {
        Player player = server.addPlayer();
        ItemStack reference = createAndHold(player, "netherite");
        UUID id = itemFactory.backpackId(reference).orElseThrow();
        double before = economy.balance;

        assertFalse(service.upgrade(player, reference));
        assertEquals("netherite", store.find(id).orElseThrow().tierId());
        assertEquals(before, economy.balance);
        assertEquals(0, economy.withdrawCalls);
    }

    @Test
    void persistenceFailureRollsBackTierAndRefundsEconomy() throws Exception {
        Player player = server.addPlayer();
        ItemStack reference = createAndHold(player, "basic");
        UUID id = itemFactory.backpackId(reference).orElseThrow();
        Path dataFile = plugin.getDataFolder().toPath().resolve("backpacks-data.csv");
        Assumptions.assumeTrue(Files.getFileStore(dataFile).supportsFileAttributeView("posix"));
        double before = economy.balance;
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(dataFile);
        try {
            Files.setPosixFilePermissions(dataFile, EnumSet.of(PosixFilePermission.OWNER_READ));
            assertFalse(service.upgrade(player, reference));
            BackpackRecord record = store.find(id).orElseThrow();
            assertEquals("basic", record.tierId());
            assertEquals(before, economy.balance);
            assertEquals(1, economy.withdrawCalls);
            assertEquals(1, economy.refundCalls);
        } finally {
            Files.setPosixFilePermissions(dataFile, original);
        }
    }

    private ItemStack createAndHold(Player player, String tierId) {
        TierDefinition tier = config.tier(tierId).orElseThrow();
        ItemStack reference = service.createBackpack(tier);
        player.getInventory().setItemInMainHand(reference);
        return reference;
    }

    private static final class FakeEconomy implements EconomyGateway {
        private double balance;
        private int withdrawCalls;
        private int refundCalls;

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
            refundCalls++;
            return true;
        }
    }
}
