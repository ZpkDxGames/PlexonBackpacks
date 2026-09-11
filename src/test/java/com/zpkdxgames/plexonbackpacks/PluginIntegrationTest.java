package com.zpkdxgames.plexonbackpacks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.api.PlexonBackpacksAPI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PluginIntegrationTest {
    private ServerMock server;
    private PlexonBackpacksPlugin plugin;
    private PlexonBackpacksAPI api;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        api = Bukkit.getServicesManager().load(PlexonBackpacksAPI.class);
        assertNotNull(api);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createRegistersAuthoritativeStateBeforeCustodyTransfer() {
        ItemStack backpack = api.create("basic");
        UUID id = api.backpackId(backpack).orElseThrow();
        assertTrue(api.backpack(id).isPresent());
        assertEquals("basic", api.backpack(id).orElseThrow().tierId());
    }

    @Test
    void clonedPhysicalReferenceRetainsSameUuidIdentity() {
        ItemStack backpack = api.create("basic");
        UUID id = api.backpackId(backpack).orElseThrow();
        assertEquals(id, api.backpackId(backpack.clone()).orElseThrow());
    }

    @Test
    void samePlayerCannotOpenTwoAuthoritativeSessions() {
        Player player = server.addPlayer();
        ItemStack first = api.create("basic");
        ItemStack second = api.create("basic");
        player.getInventory().setItemInMainHand(first);
        assertTrue(api.open(player, first));
        assertFalse(api.open(player, second));
        assertTrue(api.openSession(player.getUniqueId()).isPresent());
    }

    @Test
    void secondPlayerCannotOpenClonedReferenceConcurrently() {
        Player firstPlayer = server.addPlayer();
        Player secondPlayer = server.addPlayer();
        secondPlayer.setOp(true);
        ItemStack backpack = api.create("basic");
        ItemStack clone = backpack.clone();
        firstPlayer.getInventory().setItemInMainHand(backpack);
        secondPlayer.getInventory().setItemInMainHand(clone);
        assertTrue(api.open(firstPlayer, backpack));
        assertFalse(api.open(secondPlayer, clone));
        UUID id = api.backpackId(backpack).orElseThrow();
        assertEquals(firstPlayer.getUniqueId(), api.openSessionForBackpack(id).orElseThrow().playerId());
    }

    @Test
    void closeReleasesBothSessionIndexesAndKeepsRecord() {
        Player player = server.addPlayer();
        ItemStack backpack = api.create("basic");
        UUID id = api.backpackId(backpack).orElseThrow();
        player.getInventory().setItemInMainHand(backpack);
        assertTrue(api.open(player, backpack));
        player.closeInventory();
        assertTrue(api.openSession(player.getUniqueId()).isEmpty());
        assertTrue(api.openSessionForBackpack(id).isEmpty());
        assertTrue(api.backpack(id).isPresent());
    }

    @Test
    void openingAndMetadataRefreshPreserveForeignPdc() {
        Player player = server.addPlayer();
        ItemStack backpack = api.create("basic");
        NamespacedKey foreign = new NamespacedKey("other-plugin", "custom_identity");
        var meta = backpack.getItemMeta();
        meta.getPersistentDataContainer().set(foreign, PersistentDataType.STRING, "keep-me");
        backpack.setItemMeta(meta);
        player.getInventory().setItemInMainHand(backpack);
        assertTrue(api.open(player, backpack));
        ItemStack refreshed = player.getInventory().getItemInMainHand();
        assertEquals("keep-me", refreshed.getItemMeta().getPersistentDataContainer()
                .get(foreign, PersistentDataType.STRING));
    }

    @Test
    void failedReloadRestoresLastKnownGoodRuntimeConfig() throws Exception {
        Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
        String lastGoodFile = Files.readString(configPath);
        boolean quickDepositBefore = plugin.getConfig().getBoolean("settings.quick-deposit-enabled", true);
        int basicSlotsBefore = api.tiers().stream()
                .filter(tier -> tier.id().equals("basic"))
                .findFirst().orElseThrow().slots();

        YamlConfiguration candidate = YamlConfiguration.loadConfiguration(configPath.toFile());
        candidate.set("settings.quick-deposit-enabled", !quickDepositBefore);
        candidate.set("tiers.basic.upgrade-cost", -1.0D);
        candidate.save(configPath.toFile());
        String rejectedFile = Files.readString(configPath);

        assertFalse(plugin.reloadPlugin());
        assertEquals(quickDepositBefore,
                plugin.getConfig().getBoolean("settings.quick-deposit-enabled", !quickDepositBefore),
                "failed reload must restore dynamic settings used by the live runtime");
        assertEquals(basicSlotsBefore, api.tiers().stream()
                        .filter(tier -> tier.id().equals("basic"))
                        .findFirst().orElseThrow().slots(),
                "failed reload must retain the last runtime-accepted tier map");
        assertEquals(rejectedFile, Files.readString(configPath),
                "external config candidate must remain on disk for administrator correction");

        Files.writeString(configPath, lastGoodFile);
        assertTrue(plugin.reloadPlugin());
        assertEquals(quickDepositBefore,
                plugin.getConfig().getBoolean("settings.quick-deposit-enabled", !quickDepositBefore));
    }

    @Test
    void shutdownWritesSchemaMarkerAndFinalPersistence() throws Exception {
        ItemStack backpack = api.create("basic");
        UUID id = api.backpackId(backpack).orElseThrow();
        Bukkit.getPluginManager().disablePlugin(plugin);
        assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve("schema-version.txt")));
        assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve("backpacks-data.csv")));
        assertTrue(Files.readString(plugin.getDataFolder().toPath().resolve("backpacks-data.csv")).contains(id.toString()));
    }
}
