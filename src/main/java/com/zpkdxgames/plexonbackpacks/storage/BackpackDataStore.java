package com.zpkdxgames.plexonbackpacks.storage;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class BackpackDataStore {
    private record Snapshot(long version, String yaml) {
    }

    private final PlexonBackpacksPlugin plugin;
    private final File dataFile;
    private final Map<UUID, BackpackRecord> records = new HashMap<>();
    private final AtomicReference<Snapshot> pendingSnapshot = new AtomicReference<>();
    private final AtomicBoolean writerRunning = new AtomicBoolean();
    private final AtomicLong versionCounter = new AtomicLong();
    private final Object ioLock = new Object();
    private volatile long writtenVersion;
    private volatile boolean dirty;
    private volatile boolean shuttingDown;

    public BackpackDataStore(PlexonBackpacksPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "backpacks-data.yml");
    }

    public void load() {
        records.clear();
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(dataFile);
        } catch (IOException | InvalidConfigurationException exception) {
            preserveCorruptFile(exception);
            return;
        }

        ConfigurationSection root = yaml.getConfigurationSection("backpacks");
        if (root == null) {
            return;
        }

        int skipped = 0;
        for (String idText : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idText);
                ConfigurationSection section = root.getConfigurationSection(idText);
                if (section == null) {
                    skipped++;
                    continue;
                }
                String tier = section.getString("tier");
                if (tier == null || tier.isBlank()) {
                    skipped++;
                    continue;
                }
                UUID owner = parseUuid(section.getString("owner"));
                long createdAt = section.getLong("created-at", System.currentTimeMillis());
                long lastAccess = section.getLong("last-access", createdAt);
                ItemStack[] contents = readContents(section.getList("contents"));
                records.put(id, new BackpackRecord(id, tier, owner, createdAt, lastAccess, contents));
            } catch (IllegalArgumentException exception) {
                skipped++;
            }
        }

        plugin.getLogger().info("Loaded " + records.size() + " backpack record(s)."
                + (skipped == 0 ? "" : " Skipped " + skipped + " invalid record(s)."));
    }

    public Optional<BackpackRecord> find(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    public BackpackRecord register(UUID id, String tierId, UUID owner, int size) {
        BackpackRecord existing = records.get(id);
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        BackpackRecord record = new BackpackRecord(id, tierId, owner, now, now, new ItemStack[size]);
        records.put(id, record);
        markDirty();
        return record;
    }

    public Collection<BackpackRecord> records() {
        return List.copyOf(records.values());
    }

    public void markDirty() {
        dirty = true;
    }

    public void requestSave() {
        if (shuttingDown || !dirty) {
            return;
        }
        pendingSnapshot.set(createSnapshot());
        if (writerRunning.compareAndSet(false, true)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::drainSnapshots);
        }
    }

    public void flushSync() {
        shuttingDown = true;
        saveSync();
        pendingSnapshot.set(null);
    }

    public void saveSync() {
        Snapshot snapshot = createSnapshot();
        writeSnapshot(snapshot);
    }

    private Snapshot createSnapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", 1);
        for (BackpackRecord record : records.values()) {
            String path = "backpacks." + record.id();
            yaml.set(path + ".tier", record.tierId());
            yaml.set(path + ".owner", record.owner() == null ? null : record.owner().toString());
            yaml.set(path + ".created-at", record.createdAt());
            yaml.set(path + ".last-access", record.lastAccess());

            ItemStack[] contents = record.contents();
            List<ItemStack> serialized = new ArrayList<>(contents.length);
            for (ItemStack item : contents) {
                serialized.add(item == null ? null : item.clone());
            }
            yaml.set(path + ".contents", serialized);
        }
        dirty = false;
        return new Snapshot(versionCounter.incrementAndGet(), yaml.saveToString());
    }

    private void drainSnapshots() {
        try {
            Snapshot snapshot;
            while ((snapshot = pendingSnapshot.getAndSet(null)) != null) {
                writeSnapshot(snapshot);
            }
        } finally {
            writerRunning.set(false);
            if (!shuttingDown && pendingSnapshot.get() != null && writerRunning.compareAndSet(false, true)) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::drainSnapshots);
            }
        }
    }

    private void writeSnapshot(Snapshot snapshot) {
        synchronized (ioLock) {
            if (snapshot.version() <= writtenVersion) {
                return;
            }
            try {
                Files.createDirectories(dataFile.toPath().getParent());
                Path temporary = dataFile.toPath().resolveSibling(dataFile.getName() + ".tmp");
                Files.writeString(temporary, snapshot.yaml(), StandardCharsets.UTF_8);
                try {
                    Files.move(
                            temporary,
                            dataFile.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                writtenVersion = snapshot.version();
            } catch (IOException exception) {
                dirty = true;
                plugin.getLogger().severe("Could not save backpacks-data.yml: " + exception.getMessage());
            }
        }
    }

    private void preserveCorruptFile(Exception exception) {
        String backupName = "backpacks-data.corrupt-" + Instant.now().toEpochMilli() + ".yml";
        Path backup = dataFile.toPath().resolveSibling(backupName);
        try {
            Files.move(dataFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().severe("backpacks-data.yml could not be read and was preserved as "
                    + backupName + ": " + exception.getMessage());
        } catch (IOException moveException) {
            plugin.getLogger().severe("backpacks-data.yml could not be read or preserved: "
                    + exception.getMessage());
        }
    }

    private static UUID parseUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return UUID.fromString(text);
    }

    private static ItemStack[] readContents(List<?> list) {
        if (list == null || list.isEmpty()) {
            return new ItemStack[0];
        }
        ItemStack[] contents = new ItemStack[Math.min(54, list.size())];
        for (int index = 0; index < contents.length; index++) {
            Object value = list.get(index);
            if (value instanceof ItemStack item) {
                contents[index] = item.clone();
            }
        }
        return contents;
    }
}
