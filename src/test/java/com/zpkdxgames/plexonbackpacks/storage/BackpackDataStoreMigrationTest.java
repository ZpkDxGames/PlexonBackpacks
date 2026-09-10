package com.zpkdxgames.plexonbackpacks.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BackpackDataStoreMigrationTest {
    private PlexonBackpacksPlugin plugin;
    private ConfigManager config;
    private Path dataFolder;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        Bukkit.getPluginManager().disablePlugin(plugin);
        dataFolder = plugin.getDataFolder().toPath();
        deleteIfExists(dataFolder.resolve("backpacks-data.csv"));
        deleteIfExists(dataFolder.resolve("backpacks-data.yml"));
        deleteIfExists(dataFolder.resolve("schema-version.txt"));
        deleteTree(dataFolder.resolve("backups"));
        config = new ConfigManager(plugin);
        config.reload();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void validLegacyMigrationBacksUpAndIsIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        YamlConfiguration legacy = new YamlConfiguration();
        String root = "backpacks." + id;
        legacy.set(root + ".tier", "basic");
        legacy.set(root + ".owner", "");
        legacy.set(root + ".created-at", 10L);
        legacy.set(root + ".last-access", 20L);
        legacy.set(root + ".contents", List.of(new ItemStack(Material.DIAMOND, 3)));
        legacy.save(dataFolder.resolve("backpacks-data.yml").toFile());

        BackpackDataStore first = new BackpackDataStore(plugin, config);
        first.load();
        assertEquals(3, first.find(id).orElseThrow().contents()[0].getAmount());
        assertTrue(Files.exists(dataFolder.resolve("backups/pre-2.0/backpacks-data.yml")));
        assertEquals("2", Files.readString(dataFolder.resolve("schema-version.txt")).trim());
        assertTrue(Files.exists(dataFolder.resolve("backpacks-data.csv")));

        BackpackDataStore second = new BackpackDataStore(plugin, config);
        second.load();
        assertEquals(3, second.find(id).orElseThrow().contents()[0].getAmount());
    }

    @Test
    void malformedLegacyRecordFailsClosedWithoutInventingState() throws Exception {
        UUID id = UUID.randomUUID();
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("backpacks." + id + ".owner", "");
        legacy.save(dataFolder.resolve("backpacks-data.yml").toFile());

        BackpackDataStore store = new BackpackDataStore(plugin, config);
        assertThrows(IllegalStateException.class, store::load);
        assertTrue(store.records().isEmpty());
        assertTrue(Files.exists(dataFolder.resolve("backpacks-data.yml")));
        assertTrue(Files.exists(dataFolder.resolve("backups/pre-2.0/backpacks-data.yml")));
    }

    @Test
    void malformedCsvFailsClosedInsteadOfSkippingRow() throws Exception {
        String malformed = "id,tier,owner,created_at_ms,last_access_ms,contents_base64\nnot-a-uuid,basic,,1,2,AA==\n";
        Files.writeString(dataFolder.resolve("backpacks-data.csv"), malformed, StandardCharsets.UTF_8);
        BackpackDataStore store = new BackpackDataStore(plugin, config);
        assertThrows(IllegalStateException.class, store::load);
        assertTrue(store.records().isEmpty());
        assertEquals(malformed, Files.readString(dataFolder.resolve("backpacks-data.csv")));
    }

    @Test
    void oversizedPersistedInventoryFailsClosedInsteadOfTrimmingItems() throws Exception {
        ItemStack[] oversized = new ItemStack[55];
        oversized[54] = new ItemStack(Material.NETHERITE_INGOT);
        String encoded = Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(oversized));
        String csv = "id,tier,owner,created_at_ms,last_access_ms,contents_base64\n"
                + UUID.randomUUID() + ",netherite,,1,2," + encoded + "\n";
        Files.writeString(dataFolder.resolve("backpacks-data.csv"), csv, StandardCharsets.UTF_8);
        BackpackDataStore store = new BackpackDataStore(plugin, config);
        assertThrows(IllegalStateException.class, store::load);
        assertTrue(store.records().isEmpty());
    }

    @Test
    void csvParserPreservesQuotedFields() {
        assertEquals(List.of("id", "tier,quoted", "owner", "1", "2", "payload"),
                BackpackDataStore.parseCsvLine("id,\"tier,quoted\",owner,1,2,payload"));
        assertThrows(IllegalArgumentException.class,
                () -> BackpackDataStore.parseCsvLine("id,\"unterminated"));
    }

    private static void deleteIfExists(Path path) throws Exception {
        Files.deleteIfExists(path);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
