package com.zpkdxgames.plexonbackpacks.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BackpackDataStoreFailureTest {
    private PlexonBackpacksPlugin plugin;
    private ConfigManager config;
    private Path csv;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        Bukkit.getPluginManager().disablePlugin(plugin);
        config = new ConfigManager(plugin);
        config.reload();
        csv = plugin.getDataFolder().toPath().resolve("backpacks-data.csv");
        Files.deleteIfExists(csv);
        Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("schema-version.txt"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void synchronousCaptureFailureReturnsFalseAndPreservesCommittedDiskState() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(false);
        BackpackDataStore store = new BackpackDataStore(plugin, config, contents -> {
            if (fail.get()) {
                throw new IllegalStateException("synthetic serialization failure");
            }
            return ItemStack.serializeItemsAsBytes(contents);
        });
        store.load();

        UUID id = UUID.randomUUID();
        BackpackRecord record = store.register(id, "basic", null, 9);
        record.contents(new ItemStack[]{new ItemStack(Material.STONE)});
        store.markDirty(id);
        assertTrue(store.saveSync());
        String committed = Files.readString(csv);

        ItemStack[] changed = new ItemStack[9];
        changed[0] = new ItemStack(Material.DIAMOND, 4);
        record.contents(changed);
        store.markDirty(id);
        fail.set(true);

        assertFalse(store.saveSync());
        assertEquals(committed, Files.readString(csv));
        assertTrue(store.dirtyCount() > 0);
        assertTrue(store.lastFailure().contains(id.toString()));
        assertTrue(store.lastFailure().contains("synthetic serialization failure"));
    }

    @Test
    void oneCaptureFailurePreventsPartialBatchCommit() throws Exception {
        AtomicBoolean failOnDiamond = new AtomicBoolean(false);
        BackpackDataStore store = new BackpackDataStore(plugin, config, contents -> {
            if (failOnDiamond.get()) {
                for (ItemStack item : contents) {
                    if (item != null && item.getType() == Material.DIAMOND) {
                        throw new IllegalStateException("diamond capture rejected");
                    }
                }
            }
            return ItemStack.serializeItemsAsBytes(contents);
        });
        store.load();

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        BackpackRecord first = store.register(firstId, "basic", null, 9);
        BackpackRecord second = store.register(secondId, "basic", null, 9);
        first.contents(new ItemStack[]{new ItemStack(Material.STONE)});
        second.contents(new ItemStack[]{new ItemStack(Material.DIRT)});
        store.markDirty(firstId);
        store.markDirty(secondId);
        assertTrue(store.saveSync());
        String committed = Files.readString(csv);

        first.contents(new ItemStack[]{new ItemStack(Material.GOLD_INGOT)});
        second.contents(new ItemStack[]{new ItemStack(Material.DIAMOND)});
        store.markDirty(firstId);
        store.markDirty(secondId);
        failOnDiamond.set(true);

        assertFalse(store.saveSync());
        assertEquals(committed, Files.readString(csv));
        assertEquals(2, store.dirtyCount());
    }

    @Test
    void compactionCannotPublishCapturedButUnwrittenCandidateRows() throws Exception {
        BackpackDataStore store = new BackpackDataStore(plugin, config);
        store.load();

        UUID id = UUID.randomUUID();
        BackpackRecord record = store.register(id, "basic", null, 9);
        ItemStack[] committedContents = new ItemStack[9];
        committedContents[0] = new ItemStack(Material.STONE, 2);
        record.contents(committedContents);
        store.markDirty(id);
        assertTrue(store.saveSync());

        ItemStack[] candidateContents = new ItemStack[9];
        candidateContents[0] = new ItemStack(Material.DIAMOND, 7);
        record.contents(candidateContents);
        store.markDirty(id);

        Map<?, ?> captured = captureRows(store, List.of(id));
        compactCsv(store);

        BackpackDataStore beforeCommit = new BackpackDataStore(plugin, config);
        beforeCommit.load();
        ItemStack beforeCommitItem = beforeCommit.find(id).orElseThrow().contents()[0];
        assertEquals(Material.STONE, beforeCommitItem.getType(),
                "compaction must expose only rows that completed a successful write");
        assertEquals(2, beforeCommitItem.getAmount());

        assertTrue(writeRows(store, captured));
        compactCsv(store);

        BackpackDataStore afterCommit = new BackpackDataStore(plugin, config);
        afterCommit.load();
        ItemStack afterCommitItem = afterCommit.find(id).orElseThrow().contents()[0];
        assertEquals(Material.DIAMOND, afterCommitItem.getType());
        assertEquals(7, afterCommitItem.getAmount());
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> captureRows(BackpackDataStore store, Collection<UUID> ids) throws Exception {
        Method method = BackpackDataStore.class.getDeclaredMethod("captureRows", Collection.class);
        method.setAccessible(true);
        return (Map<?, ?>) method.invoke(store, ids);
    }

    private static boolean writeRows(BackpackDataStore store, Map<?, ?> rows) throws Exception {
        Method method = BackpackDataStore.class.getDeclaredMethod("writeRows", Map.class);
        method.setAccessible(true);
        return (boolean) method.invoke(store, rows);
    }

    private static void compactCsv(BackpackDataStore store) throws Exception {
        Method method = BackpackDataStore.class.getDeclaredMethod("compactCsv");
        method.setAccessible(true);
        method.invoke(store);
    }
}
