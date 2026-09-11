package com.zpkdxgames.plexonbackpacks.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.integration.economy.EconomyGateway;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
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

class BackpackInfoEligibilityIntegrationTest {
    private ServerMock server;
    private PlexonBackpacksPlugin plugin;
    private ConfigManager config;
    private BackpackItemFactory itemFactory;
    private BackpackDataStore store;
    private BackpackService service;
    private BackpackInfoGui gui;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        config = new ConfigManager(plugin);
        config.reload();
        itemFactory = new BackpackItemFactory(plugin, config);
        store = new BackpackDataStore(plugin, config);
        store.load();
        EconomyGateway economy = new AlwaysFundedEconomy();
        service = new BackpackService(plugin, config, new Messages(plugin), itemFactory, store, economy);
        gui = new BackpackInfoGui(plugin, config, new Messages(plugin), itemFactory, service, economy);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void nonOwnerSeesOwnershipBlockBeforeUpgrade() {
        Player owner = server.addPlayer();
        ItemStack reference = service.createBackpack(config.tier("basic").orElseThrow());
        owner.getInventory().setItemInMainHand(reference);
        assertTrue(service.open(owner, reference));
        owner.closeInventory();

        UUID id = itemFactory.backpackId(reference).orElseThrow();
        BackpackRecord record = store.find(id).orElseThrow();
        TierDefinition tier = config.tier(record.tierId()).orElseThrow();
        Player visitor = server.addPlayer();

        UpgradeViewModel view = gui.upgradeView(visitor, record, tier, true);
        assertEquals(UpgradeViewModel.Status.WRONG_OWNER, view.status());
    }

    @Test
    void explicitlyDeniedUpgradePermissionIsPresentedAsMissingRequirement() {
        Player player = server.addPlayer();
        player.addAttachment(plugin, "plexonbackpacks.upgrade", false);
        ItemStack reference = service.createBackpack(config.tier("basic").orElseThrow());
        UUID id = itemFactory.backpackId(reference).orElseThrow();
        BackpackRecord record = store.find(id).orElseThrow();
        TierDefinition tier = config.tier(record.tierId()).orElseThrow();

        UpgradeViewModel view = gui.upgradeView(player, record, tier, true);
        assertEquals(UpgradeViewModel.Status.MISSING_REQUIREMENT, view.status());
    }

    private static final class AlwaysFundedEconomy implements EconomyGateway {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String providerName() {
            return "AlwaysFunded";
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
