package com.zpkdxgames.plexonbackpacks.storage;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main-thread record cache backed by an append-only CSV journal.
 *
 * <p>Only changed backpacks are serialized during an autosave. Disk appends and
 * infrequent compaction run asynchronously, avoiding the full-database YAML
 * serialization and rewrite that would otherwise occur on every save.</p>
 */
public final class BackpackDataStore {
    private static final String HEADER = "id,tier,owner,created_at_ms,last_access_ms,contents_base64";

    private record SnapshotRow(UUID id, long sequence, String line) {
    }

    private final PlexonBackpacksPlugin plugin;
    private final ConfigManager config;
    private final File dataFile;
    private final File legacyDataFile;
    private final Map<UUID, BackpackRecord> records = new HashMap<>();
    private final Set<UUID> dirtyIds = new LinkedHashSet<>();
    private final Map<UUID, String> latestRows = new ConcurrentHashMap<>();
    private final Map<UUID, Long> writtenSequences = new HashMap<>();
    private final AtomicReference<Map<UUID, SnapshotRow>> pendingRows = new AtomicReference<>();
    private final AtomicBoolean writerRunning = new AtomicBoolean();
    private final AtomicLong sequenceCounter = new AtomicLong();
    private final Object ioLock = new Object();
    private final Object queueLock = new Object();
    private Map<UUID, SnapshotRow> activeRows;
    private volatile long journalRows;
    private volatile boolean shuttingDown;

    public BackpackDataStore(PlexonBackpacksPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.dataFile = new File(plugin.getDataFolder(), "backpacks-data.csv");
        this.legacyDataFile = new File(plugin.getDataFolder(), "backpacks-data.yml");
    }

    public void load() {
        records.clear();
        dirtyIds.clear();
        latestRows.clear();
        writtenSequences.clear();
        journalRows = 0;

        if (dataFile.exists()) {
            loadCsv();
            return;
        }
        if (legacyDataFile.exists()) {
            migrateLegacyYaml();
        }
    }

    private void loadCsv() {
        int skipped = 0;
        long rowsRead = 0;
        try (BufferedReader reader = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.equals(HEADER) || line.isBlank()) {
                        continue;
                    }
                }
                if (line.isBlank()) {
                    continue;
                }

                try {
                    BackpackRecord record = decodeRow(line);
                    records.put(record.id(), record);
                    latestRows.put(record.id(), line);
                    rowsRead++;
                } catch (RuntimeException exception) {
                    skipped++;
                }
            }
        } catch (IOException exception) {
            preserveCorruptCsv(exception);
            return;
        }

        journalRows = rowsRead;
        sequenceCounter.set(rowsRead);
        plugin.getLogger().info("Loaded " + records.size() + " backpack record(s) from CSV."
                + (skipped == 0 ? "" : " Skipped " + skipped + " invalid journal row(s)."));
    }

    private BackpackRecord decodeRow(String line) {
        List<String> fields = parseCsvLine(line);
        if (fields.size() != 6) {
            throw new IllegalArgumentException("Expected six CSV columns");
        }

        UUID id = UUID.fromString(fields.get(0));
        String tier = fields.get(1);
        if (tier.isBlank()) {
            throw new IllegalArgumentException("Tier cannot be blank");
        }
        UUID owner = parseUuid(fields.get(2));
        long createdAt = Long.parseLong(fields.get(3));
        long lastAccess = Long.parseLong(fields.get(4));
        byte[] bytes = Base64.getDecoder().decode(fields.get(5));
        ItemStack[] contents = bytes.length == 0
                ? new ItemStack[0]
                : ItemStack.deserializeItemsFromBytes(bytes);
        if (contents.length > 54) {
            ItemStack[] trimmed = new ItemStack[54];
            System.arraycopy(contents, 0, trimmed, 0, trimmed.length);
            contents = trimmed;
        }
        return new BackpackRecord(id, tier, owner, createdAt, lastAccess, contents);
    }

    private void migrateLegacyYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(legacyDataFile);
        } catch (IOException | InvalidConfigurationException exception) {
            preserveCorruptLegacyFile(exception);
            return;
        }

        ConfigurationSection root = yaml.getConfigurationSection("backpacks");
        int skipped = 0;
        if (root != null) {
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
                    ItemStack[] contents = readLegacyContents(section.getList("contents"));
                    records.put(id, new BackpackRecord(id, tier, owner, createdAt, lastAccess, contents));
                } catch (IllegalArgumentException exception) {
                    skipped++;
                }
            }
        }

        dirtyIds.addAll(records.keySet());
        Map<UUID, SnapshotRow> migrationRows = captureRows(List.copyOf(dirtyIds));
        if (!writeRows(migrationRows)) {
            dirtyIds.addAll(records.keySet());
            plugin.getLogger().severe("Legacy backpack data could not be migrated to CSV.");
            return;
        }

        Path migrated = legacyDataFile.toPath().resolveSibling("backpacks-data.migrated.yml");
        try {
            Files.move(legacyDataFile.toPath(), migrated, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().warning("CSV migration succeeded, but the old YAML file could not be renamed: "
                    + exception.getMessage());
        }
        plugin.getLogger().info("Migrated " + records.size() + " backpack record(s) from YAML to CSV."
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
        markDirty(id);
        return record;
    }

    public Collection<BackpackRecord> records() {
        return List.copyOf(records.values());
    }

    public void markDirty(UUID id) {
        if (records.containsKey(id)) {
            dirtyIds.add(id);
        }
    }

    public void requestSave() {
        if (shuttingDown) {
            return;
        }
        if (!dirtyIds.isEmpty()) {
            enqueue(captureRows(List.copyOf(dirtyIds)));
        }
        startWriterIfNeeded();
    }

    public void saveSync() {
        Map<UUID, SnapshotRow> rows = new LinkedHashMap<>();
        mergeRows(rows, captureRows(List.copyOf(dirtyIds)));
        synchronized (queueLock) {
            mergeRows(rows, activeRows);
            mergeRows(rows, pendingRows.getAndSet(null));
        }
        if (!writeRows(rows)) {
            dirtyIds.addAll(rows.keySet());
        }
    }

    public void flushSync() {
        shuttingDown = true;
        saveSync();
    }

    private Map<UUID, SnapshotRow> captureRows(Collection<UUID> ids) {
        Map<UUID, SnapshotRow> snapshots = new LinkedHashMap<>();
        for (UUID id : ids) {
            BackpackRecord record = records.get(id);
            if (record == null) {
                dirtyIds.remove(id);
                continue;
            }
            try {
                long sequence = sequenceCounter.incrementAndGet();
                String line = encodeRow(record);
                SnapshotRow row = new SnapshotRow(id, sequence, line);
                snapshots.put(id, row);
                latestRows.put(id, line);
                dirtyIds.remove(id);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Could not serialize backpack " + id + ": "
                        + exception.getMessage());
            }
        }
        return snapshots;
    }

    private String encodeRow(BackpackRecord record) {
        byte[] contents = ItemStack.serializeItemsAsBytes(record.contents());
        return String.join(",",
                escapeCsv(record.id().toString()),
                escapeCsv(record.tierId()),
                escapeCsv(record.owner() == null ? "" : record.owner().toString()),
                Long.toString(record.createdAt()),
                Long.toString(record.lastAccess()),
                Base64.getEncoder().encodeToString(contents)
        );
    }

    private void enqueue(Map<UUID, SnapshotRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        pendingRows.getAndUpdate(existing -> {
            Map<UUID, SnapshotRow> merged = existing == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(existing);
            rows.forEach((id, row) -> merged.merge(
                    id,
                    row,
                    (left, right) -> left.sequence() >= right.sequence() ? left : right
            ));
            return merged;
        });
    }

    private void startWriterIfNeeded() {
        if (pendingRows.get() == null || !writerRunning.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::drainRows);
    }

    private void drainRows() {
        boolean failed = false;
        try {
            Map<UUID, SnapshotRow> rows;
            while (true) {
                synchronized (queueLock) {
                    rows = pendingRows.getAndSet(null);
                    activeRows = rows;
                }
                if (rows == null) {
                    break;
                }
                if (!writeRows(rows)) {
                    enqueue(rows);
                    failed = true;
                    break;
                }
                synchronized (queueLock) {
                    if (activeRows == rows) {
                        activeRows = null;
                    }
                }
            }
        } finally {
            synchronized (queueLock) {
                activeRows = null;
            }
            writerRunning.set(false);
            if (!failed && !shuttingDown && pendingRows.get() != null) {
                startWriterIfNeeded();
            }
        }
    }

    private static void mergeRows(
            Map<UUID, SnapshotRow> target,
            Map<UUID, SnapshotRow> source
    ) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((id, row) -> target.merge(
                id,
                row,
                (left, right) -> left.sequence() >= right.sequence() ? left : right
        ));
    }

    private boolean writeRows(Map<UUID, SnapshotRow> rows) {
        if (rows.isEmpty()) {
            return true;
        }
        synchronized (ioLock) {
            try {
                Files.createDirectories(dataFile.toPath().getParent());
                boolean needsHeader = !dataFile.exists() || Files.size(dataFile.toPath()) == 0;
                List<SnapshotRow> written = new ArrayList<>(rows.size());
                for (SnapshotRow row : rows.values()) {
                    long previous = writtenSequences.getOrDefault(row.id(), -1L);
                    if (row.sequence() <= previous) {
                        continue;
                    }
                    written.add(row);
                }
                if (needsHeader || !written.isEmpty()) {
                    try (BufferedWriter writer = Files.newBufferedWriter(
                            dataFile.toPath(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    )) {
                        if (needsHeader) {
                            writer.write(HEADER);
                            writer.newLine();
                        }
                        for (SnapshotRow row : written) {
                            writer.write(row.line());
                            writer.newLine();
                        }
                    }
                }
                for (SnapshotRow row : written) {
                    writtenSequences.put(row.id(), row.sequence());
                }
                journalRows += written.size();

                long obsoleteRows = Math.max(0L, journalRows - latestRows.size());
                if (obsoleteRows >= config.csvCompactionThresholdUpdates()) {
                    try {
                        compactCsv();
                    } catch (IOException exception) {
                        plugin.getLogger().severe("Could not compact backpacks-data.csv: "
                                + exception.getMessage());
                    }
                }
                return true;
            } catch (IOException exception) {
                plugin.getLogger().severe("Could not append backpacks-data.csv: " + exception.getMessage());
                return false;
            }
        }
    }

    private void compactCsv() throws IOException {
        Path target = dataFile.toPath();
        Path temporary = target.resolveSibling(dataFile.getName() + ".tmp");
        List<String> compactedRows = List.copyOf(latestRows.values());
        try (BufferedWriter writer = Files.newBufferedWriter(
                temporary,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            writer.write(HEADER);
            writer.newLine();
            for (String line : compactedRows) {
                writer.write(line);
                writer.newLine();
            }
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        journalRows = compactedRows.size();
    }

    private void preserveCorruptCsv(Exception exception) {
        String backupName = "backpacks-data.corrupt-" + Instant.now().toEpochMilli() + ".csv";
        Path backup = dataFile.toPath().resolveSibling(backupName);
        try {
            Files.move(dataFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().severe("backpacks-data.csv could not be read and was preserved as "
                    + backupName + ": " + exception.getMessage());
        } catch (IOException moveException) {
            plugin.getLogger().severe("backpacks-data.csv could not be read or preserved: "
                    + exception.getMessage());
        }
    }

    private void preserveCorruptLegacyFile(Exception exception) {
        String backupName = "backpacks-data.corrupt-" + Instant.now().toEpochMilli() + ".yml";
        Path backup = legacyDataFile.toPath().resolveSibling(backupName);
        try {
            Files.move(legacyDataFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().severe("Legacy backpacks-data.yml could not be read and was preserved as "
                    + backupName + ": " + exception.getMessage());
        } catch (IOException moveException) {
            plugin.getLogger().severe("Legacy backpacks-data.yml could not be read or preserved: "
                    + exception.getMessage());
        }
    }

    private static UUID parseUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return UUID.fromString(text);
    }

    private static ItemStack[] readLegacyContents(List<?> list) {
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

    private static String escapeCsv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>(6);
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Unterminated CSV quote");
        }
        fields.add(value.toString());
        return fields;
    }
}
