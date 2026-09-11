package com.zpkdxgames.plexonbackpacks.storage;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.service.BackpackCapacityInvariant;
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
 * <p>Live Bukkit ItemStacks are copied and serialized on the primary thread.
 * Only immutable encoded rows are handed to the asynchronous disk writer.</p>
 */
public final class BackpackDataStore {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    private static final String HEADER = "id,tier,owner,created_at_ms,last_access_ms,contents_base64";

    @FunctionalInterface
    interface ItemStackEncoder {
        byte[] encode(ItemStack[] contents);
    }

    private record SnapshotRow(UUID id, long sequence, String line) {
    }

    private final PlexonBackpacksPlugin plugin;
    private final ConfigManager config;
    private final File dataFile;
    private final File legacyDataFile;
    private final File schemaFile;
    private final ItemStackEncoder itemStackEncoder;
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
    private volatile String lastFailure = "NONE";

    public BackpackDataStore(PlexonBackpacksPlugin plugin, ConfigManager config) {
        this(plugin, config, ItemStack::serializeItemsAsBytes);
    }

    BackpackDataStore(PlexonBackpacksPlugin plugin, ConfigManager config, ItemStackEncoder itemStackEncoder) {
        this.plugin = plugin;
        this.config = config;
        this.dataFile = new File(plugin.getDataFolder(), "backpacks-data.csv");
        this.legacyDataFile = new File(plugin.getDataFolder(), "backpacks-data.yml");
        this.schemaFile = new File(plugin.getDataFolder(), "schema-version.txt");
        this.itemStackEncoder = itemStackEncoder;
    }

    public void load() {
        records.clear();
        dirtyIds.clear();
        latestRows.clear();
        writtenSequences.clear();
        journalRows = 0;
        shuttingDown = false;
        lastFailure = "NONE";

        try {
            int existingSchema = readSchemaVersion();
            if (existingSchema > CURRENT_SCHEMA_VERSION) {
                throw new IllegalStateException("Persistence schema " + existingSchema
                        + " is newer than supported schema " + CURRENT_SCHEMA_VERSION);
            }
            if (existingSchema == 0 && (dataFile.exists() || legacyDataFile.exists())) {
                backupPrePhase2Data();
            }

            if (dataFile.exists()) {
                loadCsvStrict();
            } else if (legacyDataFile.exists()) {
                migrateLegacyYamlStrict();
            }

            if (existingSchema < CURRENT_SCHEMA_VERSION) {
                writeSchemaVersion(CURRENT_SCHEMA_VERSION);
            }
        } catch (RuntimeException exception) {
            records.clear();
            dirtyIds.clear();
            latestRows.clear();
            lastFailure = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw exception;
        }
    }

    private int readSchemaVersion() {
        if (!schemaFile.exists()) {
            return 0;
        }
        try {
            String value = Files.readString(schemaFile.toPath(), StandardCharsets.UTF_8).trim();
            int version = Integer.parseInt(value);
            if (version < 1) {
                throw new IllegalStateException("Invalid persistence schema version: " + value);
            }
            return version;
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("Could not read schema-version.txt", exception);
        }
    }

    private void writeSchemaVersion(int version) {
        try {
            Files.createDirectories(schemaFile.toPath().getParent());
            Path temporary = schemaFile.toPath().resolveSibling(schemaFile.getName() + ".tmp");
            Files.writeString(temporary, Integer.toString(version) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            moveAtomically(temporary, schemaFile.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write persistence schema marker", exception);
        }
    }

    private void backupPrePhase2Data() {
        Path backupDir = plugin.getDataFolder().toPath().resolve("backups").resolve("pre-2.0");
        try {
            Files.createDirectories(backupDir);
            if (dataFile.exists()) {
                copyOnce(dataFile.toPath(), backupDir.resolve(dataFile.getName()));
            }
            if (legacyDataFile.exists()) {
                copyOnce(legacyDataFile.toPath(), backupDir.resolve(legacyDataFile.getName()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create mandatory pre-2.0 persistence backup", exception);
        }
    }

    private static void copyOnce(Path source, Path target) throws IOException {
        if (!Files.exists(target)) {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private void loadCsvStrict() {
        Map<UUID, BackpackRecord> loadedRecords = new LinkedHashMap<>();
        Map<UUID, String> loadedRows = new LinkedHashMap<>();
        long rowsRead = 0;
        try (BufferedReader reader = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            boolean firstLine = true;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
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
                    loadedRecords.put(record.id(), record);
                    loadedRows.put(record.id(), line);
                    rowsRead++;
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Malformed backpacks-data.csv row at line " + lineNumber, exception);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read backpacks-data.csv", exception);
        }

        records.putAll(loadedRecords);
        latestRows.putAll(loadedRows);
        journalRows = rowsRead;
        sequenceCounter.set(rowsRead);
        plugin.getLogger().info("Loaded " + records.size() + " backpack record(s) from CSV using schema "
                + CURRENT_SCHEMA_VERSION + ".");
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
            throw new IllegalArgumentException("Backpack contents exceed the supported 54-slot bound");
        }
        return new BackpackRecord(id, tier, owner, createdAt, lastAccess, contents);
    }

    private void migrateLegacyYamlStrict() {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(legacyDataFile);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Legacy backpacks-data.yml is malformed", exception);
        }

        ConfigurationSection root = yaml.getConfigurationSection("backpacks");
        Map<UUID, BackpackRecord> migrated = new LinkedHashMap<>();
        if (root != null) {
            for (String idText : root.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(idText);
                    ConfigurationSection section = root.getConfigurationSection(idText);
                    if (section == null) {
                        throw new IllegalArgumentException("Missing backpack section");
                    }
                    String tier = section.getString("tier");
                    if (tier == null || tier.isBlank()) {
                        throw new IllegalArgumentException("Missing tier");
                    }
                    UUID owner = parseUuid(section.getString("owner"));
                    long createdAt = section.getLong("created-at", System.currentTimeMillis());
                    long lastAccess = section.getLong("last-access", createdAt);
                    ItemStack[] contents = readLegacyContentsStrict(section.getList("contents"));
                    migrated.put(id, new BackpackRecord(id, tier, owner, createdAt, lastAccess, contents));
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Malformed legacy backpack record " + idText, exception);
                }
            }
        }

        records.putAll(migrated);
        dirtyIds.addAll(records.keySet());
        Map<UUID, SnapshotRow> migrationRows = captureRows(List.copyOf(dirtyIds));
        if (!writeRows(migrationRows)) {
            dirtyIds.addAll(records.keySet());
            throw new IllegalStateException("Legacy backpack data could not be migrated to CSV");
        }

        Path migratedPath = legacyDataFile.toPath().resolveSibling("backpacks-data.migrated.yml");
        try {
            Files.copy(legacyDataFile.toPath(), migratedPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException exception) {
            plugin.getLogger().warning("CSV migration succeeded, but a convenience migrated YAML copy could not be created: "
                    + exception.getMessage());
        }
        plugin.getLogger().info("Migrated " + records.size() + " backpack record(s) from legacy YAML without skips.");
    }

    public Optional<BackpackRecord> find(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    public BackpackRecord register(UUID id, String tierId, UUID owner, int size) {
        BackpackRecord existing = records.get(id);
        if (existing != null) {
            return existing;
        }
        if (size < 9 || size > 54) {
            throw new IllegalArgumentException("backpack size outside supported bounds");
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

    public int dirtyCount() {
        return dirtyIds.size();
    }

    public boolean writerRunning() {
        return writerRunning.get();
    }

    public long journalRows() {
        return journalRows;
    }

    public int schemaVersion() {
        return CURRENT_SCHEMA_VERSION;
    }

    public String lastFailure() {
        return lastFailure;
    }

    public void requestSave() {
        if (shuttingDown) {
            return;
        }
        if (!dirtyIds.isEmpty()) {
            try {
                enqueue(captureRows(List.copyOf(dirtyIds)));
            } catch (RuntimeException exception) {
                return;
            }
        }
        startWriterIfNeeded();
    }

    public boolean saveSync() {
        Map<UUID, SnapshotRow> rows = new LinkedHashMap<>();
        try {
            mergeRows(rows, captureRows(List.copyOf(dirtyIds)));
        } catch (RuntimeException exception) {
            return false;
        }

        synchronized (queueLock) {
            mergeRows(rows, activeRows);
            mergeRows(rows, pendingRows.getAndSet(null));
        }
        String failureBeforeWrite = lastFailure;
        boolean success = writeRows(rows);
        if (!success) {
            dirtyIds.addAll(rows.keySet());
            return false;
        }
        if (lastFailure.equals(failureBeforeWrite)) {
            lastFailure = "NONE";
        }
        return true;
    }

    public boolean flushSync() {
        shuttingDown = true;
        return saveSync();
    }

    private Map<UUID, SnapshotRow> captureRows(Collection<UUID> ids) {
        Map<UUID, SnapshotRow> snapshots = new LinkedHashMap<>();
        for (UUID id : ids) {
            BackpackRecord record = records.get(id);
            if (record == null) {
                continue;
            }
            try {
                long sequence = sequenceCounter.incrementAndGet();
                snapshots.put(id, new SnapshotRow(id, sequence, encodeRow(record)));
            } catch (RuntimeException exception) {
                lastFailure = "Could not serialize authoritative backpack " + id + ": "
                        + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                plugin.getLogger().severe(lastFailure);
                throw new IllegalStateException(lastFailure, exception);
            }
        }

        // Capturing produces an immutable candidate row, but it is not committed yet. In particular,
        // compaction must never observe a candidate that is still pending or waiting behind another writer.
        // The committed latestRows map advances only after writeRows finishes the append successfully.
        for (UUID id : snapshots.keySet()) {
            dirtyIds.remove(id);
        }
        ids.stream().filter(id -> !records.containsKey(id)).forEach(dirtyIds::remove);
        return snapshots;
    }

    private String encodeRow(BackpackRecord record) {
        TierDefinition tier = config.tier(record.tierId())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot persist backpack " + record.id() + " because tier '" + record.tierId() + "' is not configured"));
        int capacity = BackpackCapacityInvariant.authoritativeCapacity(record, tier);
        ItemStack[] normalized = BackpackCapacityInvariant.normalize(record.contents(), capacity);
        byte[] contents = itemStackEncoder.encode(normalized);
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

    private static void mergeRows(Map<UUID, SnapshotRow> target, Map<UUID, SnapshotRow> source) {
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
                // The append completed successfully. Only now may these rows become compaction authority.
                for (SnapshotRow row : written) {
                    writtenSequences.put(row.id(), row.sequence());
                    latestRows.put(row.id(), row.line());
                }
                journalRows += written.size();

                long obsoleteRows = Math.max(0L, journalRows - latestRows.size());
                if (obsoleteRows >= config.csvCompactionThresholdUpdates()) {
                    try {
                        compactCsv();
                    } catch (IOException exception) {
                        lastFailure = "Could not compact backpacks-data.csv: " + exception.getMessage();
                        plugin.getLogger().severe(lastFailure);
                    }
                }
                return true;
            } catch (IOException exception) {
                lastFailure = "Could not append backpacks-data.csv: " + exception.getMessage();
                plugin.getLogger().severe(lastFailure);
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
        moveAtomically(temporary, target);
        journalRows = compactedRows.size();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static UUID parseUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return UUID.fromString(text);
    }

    private static ItemStack[] readLegacyContentsStrict(List<?> list) {
        if (list == null || list.isEmpty()) {
            return new ItemStack[0];
        }
        if (list.size() > 54) {
            throw new IllegalArgumentException("Legacy backpack contents exceed 54 slots");
        }
        ItemStack[] contents = new ItemStack[list.size()];
        for (int index = 0; index < contents.length; index++) {
            Object value = list.get(index);
            if (value == null) {
                continue;
            }
            if (!(value instanceof ItemStack item)) {
                throw new IllegalArgumentException("Legacy slot " + index + " is not an ItemStack");
            }
            contents[index] = item.clone();
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

    static List<String> parseCsvLine(String line) {
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
