package com.zpkdxgames.plexonbackpacks;

import com.zpkdxgames.plexonbackpacks.api.PlexonBackpacksAPI;
import com.zpkdxgames.plexonbackpacks.api.internal.DefaultPlexonBackpacksAPI;
import com.zpkdxgames.plexonbackpacks.command.BackpackCommand;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.integration.core.CoreBridge;
import com.zpkdxgames.plexonbackpacks.integration.core.CoreBridgeFactory;
import com.zpkdxgames.plexonbackpacks.integration.economy.EconomyGateway;
import com.zpkdxgames.plexonbackpacks.integration.economy.EconomyGatewayFactory;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.listener.AdminMenuListener;
import com.zpkdxgames.plexonbackpacks.listener.BackpackListener;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.recipe.RecipeRegistry;
import com.zpkdxgames.plexonbackpacks.service.AdminMenuService;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import com.zpkdxgames.plexonbackpacks.storage.BackpackDataStore;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlexonBackpacksPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private Messages messages;
    private BackpackDataStore dataStore;
    private BackpackItemFactory itemFactory;
    private BackpackService backpackService;
    private AdminMenuService adminMenuService;
    private RecipeRegistry recipeRegistry;
    private BukkitTask autosaveTask;
    private CoreBridge coreBridge;
    private EconomyGateway economyGateway;
    private PlexonBackpacksAPI publicApi;
    private boolean publicApiRegistered;

    @Override
    public void onEnable() {
        coreBridge = CoreBridgeFactory.resolve(this);
        try {
            coreBridge.registerStarting();
            saveDefaultConfig();
            migrateConfigIfNeeded();

            configManager = new ConfigManager(this);
            configManager.reload();
            messages = new Messages(this);
            dataStore = new BackpackDataStore(this, configManager);
            dataStore.load();
            itemFactory = new BackpackItemFactory(this, configManager);
            economyGateway = EconomyGatewayFactory.resolve(this);
            backpackService = new BackpackService(
                    this, configManager, messages, itemFactory, dataStore, economyGateway);
            adminMenuService = new AdminMenuService(configManager, itemFactory);
            recipeRegistry = new RecipeRegistry(this, configManager, itemFactory, backpackService, messages);

            getServer().getPluginManager().registerEvents(
                    new BackpackListener(backpackService, itemFactory, messages), this);
            getServer().getPluginManager().registerEvents(
                    new AdminMenuListener(configManager, messages, itemFactory, backpackService), this);
            getServer().getPluginManager().registerEvents(recipeRegistry, this);

            PluginCommand command = getCommand("backpack");
            if (command == null) {
                throw new IllegalStateException("The backpack command is missing from plugin.yml");
            }
            BackpackCommand executor = new BackpackCommand(
                    this, configManager, messages, itemFactory, backpackService, adminMenuService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);

            recipeRegistry.registerAll();
            registerPublicApi();
            restartAutosave();
            coreBridge.markReady("Backpack persistence, custody sessions, premium GUI and public API are operational");
            getLogger().info("PlexonBackpacks " + getPluginMeta().getVersion() + " enabled with "
                    + configManager.tiers().size() + " tier(s) in " + coreBridge.mode() + " mode. Economy: "
                    + backpackService.economyProvider());
        } catch (RuntimeException exception) {
            if (coreBridge != null) {
                coreBridge.markFailed("Startup failed: " + exception.getMessage());
            }
            getLogger().severe("PlexonBackpacks cannot start: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (recipeRegistry != null) {
            recipeRegistry.unregisterAll();
        }
        if (backpackService != null) {
            backpackService.closeAll();
            backpackService.snapshotOpenSessions();
        }
        if (dataStore != null && !dataStore.flushSync()) {
            getLogger().severe("CRITICAL: final synchronous backpack persistence flush failed during shutdown.");
        }
        unregisterPublicApi();
        if (coreBridge != null) {
            coreBridge.unregister();
        }
    }

    public boolean reloadPlugin() {
        reloadConfig();
        try {
            configManager.reload();
        } catch (RuntimeException exception) {
            getLogger().severe("Could not reload config.yml: " + exception.getMessage());
            coreBridge.markDegraded("Configuration reload failed: " + exception.getMessage());
            return false;
        }

        backpackService.closeAll();
        adminMenuService.reload();
        recipeRegistry.registerAll();
        restartAutosave();
        coreBridge.markReady("Reload completed; backpack services remain operational");
        return true;
    }

    public boolean saveBackpacksNow() {
        backpackService.snapshotOpenSessions();
        return dataStore.saveSync();
    }

    public DiagnosticsSnapshot diagnostics() {
        boolean publicationReady = publicApiRegistered
                && dataStore.schemaVersion() == BackpackDataStore.CURRENT_SCHEMA_VERSION
                && "NONE".equals(dataStore.lastFailure())
                && backpackService.openSessionCount() == backpackService.activeLockCount();
        return new DiagnosticsSnapshot(
                getPluginMeta().getVersion(),
                Bukkit.getVersion(),
                System.getProperty("java.version", "unknown"),
                coreBridge.mode(),
                coreBridge.installed(),
                coreBridge.compatible(),
                coreBridge.pluginVersion(),
                coreBridge.apiVersion(),
                CoreBridge.SUPPORTED_API_RANGE,
                coreBridge.registrationState(),
                coreBridge.detail(),
                configManager.tiers().size(),
                dataStore.records().size(),
                backpackService.openSessionCount(),
                backpackService.activeLockCount(),
                configManager.csvCompactionThresholdUpdates(),
                dataStore.schemaVersion(),
                dataStore.dirtyCount(),
                dataStore.writerRunning(),
                dataStore.journalRows(),
                dataStore.lastFailure(),
                backpackService.economyProvider(),
                publicApiRegistered,
                publicationReady);
    }

    private void registerPublicApi() {
        if (publicApiRegistered) {
            return;
        }
        publicApi = new DefaultPlexonBackpacksAPI(configManager, itemFactory, backpackService);
        Bukkit.getServicesManager().register(PlexonBackpacksAPI.class, publicApi, this, ServicePriority.Normal);
        publicApiRegistered = true;
    }

    private void unregisterPublicApi() {
        if (!publicApiRegistered || publicApi == null) {
            return;
        }
        Bukkit.getServicesManager().unregister(PlexonBackpacksAPI.class, publicApi);
        publicApiRegistered = false;
        publicApi = null;
    }

    private void restartAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        long interval = configManager.autosaveIntervalTicks();
        if (interval <= 0) {
            return;
        }
        autosaveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            backpackService.snapshotOpenSessions();
            dataStore.requestSave();
        }, interval, interval);
    }

    private void migrateConfigIfNeeded() {
        int version = getConfig().getInt("config-version", 1);
        if (version < 2) {
            updateDefaultTexture(
                    "tiers.basic.texture",
                    "8351e505989838e27287e7afbc7f97e796cab5f3598a76160c131c940d0c5",
                    "http://textures.minecraft.net/texture/8351e505989838e27287e7afbc7f97e796cab5f3598a76160c131c940d0c5");
            updateDefaultTexture(
                    "tiers.iron.texture",
                    "ebdf8d53bdb932c223c627bbb8c1e0c5e351a616cd8056929c66e6dce44433db",
                    "http://textures.minecraft.net/texture/ebdf8d53bdb932c223c627bbb8c1e0c5e351a616cd8056929c66e6dce44433db");
            updateDefaultTexture(
                    "tiers.gold.texture",
                    "51bbbc5c24384ecb2f6844da285cccf9eb011c7a6670177cf75cd65513bc1274",
                    "http://textures.minecraft.net/texture/a37a35522f67b2af92345592846b702b9afb9d7c8dbad5ea150673c9e44de3");
            updateDefaultTexture(
                    "tiers.diamond.texture",
                    "10d1b0732bf7a70de4dc01559cc5c9811068ef7b6095010382709f94093927f6",
                    "http://textures.minecraft.net/texture/df70fab3246fe027ce0bba885a73c6e82d8ff8f358231e8461f956560cfa58f");
            updateDefaultTexture(
                    "tiers.netherite.texture",
                    "835d7cc09fffbca3e1c00d421afaa432cf71fcb09555f54523e5220d1af0f97d",
                    "http://textures.minecraft.net/texture/a9ab1fdcbe878d1e55bdd43cebc5e43836a6da69541f4a233fe88f1305668");
            getConfig().set("settings.give-binds-to-recipient", null);
            if (!getConfig().isSet("settings.csv-compaction-threshold-updates")) {
                getConfig().set("settings.csv-compaction-threshold-updates", 10_000);
            }
            version = 2;
        }

        if (version < 3) {
            if (!getConfig().isSet("settings.upgrades-enabled")) {
                getConfig().set("settings.upgrades-enabled", true);
            }
            if (!getConfig().isSet("settings.quick-deposit-enabled")) {
                getConfig().set("settings.quick-deposit-enabled", true);
            }
            Map<String, Double> defaults = Map.of(
                    "basic", 0.0D,
                    "iron", 5_000.0D,
                    "gold", 15_000.0D,
                    "diamond", 35_000.0D,
                    "netherite", 75_000.0D);
            defaults.forEach((tier, cost) -> {
                String path = "tiers." + tier + ".upgrade-cost";
                if (!getConfig().isSet(path)) {
                    getConfig().set(path, cost);
                }
            });
            version = 3;
        }

        if (getConfig().getInt("config-version", 1) != version) {
            getConfig().set("config-version", version);
            saveConfig();
            getLogger().info("Updated config.yml to format version " + version + ".");
        }
    }

    private void updateDefaultTexture(String path, String previousHash, String replacementUrl) {
        String current = getConfig().getString(path, "");
        if (current.equals(previousHash) || current.endsWith("/" + previousHash)) {
            getConfig().set(path, replacementUrl);
        }
    }

    public record DiagnosticsSnapshot(
            String pluginVersion,
            String platformVersion,
            String javaVersion,
            String mode,
            boolean coreInstalled,
            boolean coreCompatible,
            String corePluginVersion,
            String coreApiVersion,
            String supportedCoreRange,
            String moduleState,
            String coreDetail,
            int tiers,
            int backpackRecords,
            int openSessions,
            int activeLocks,
            long compactionThreshold,
            int schemaVersion,
            int dirtyRecords,
            boolean writerRunning,
            long journalRows,
            String lastPersistenceFailure,
            String economyProvider,
            boolean publicApiRegistered,
            boolean publicationReady
    ) {
    }
}
