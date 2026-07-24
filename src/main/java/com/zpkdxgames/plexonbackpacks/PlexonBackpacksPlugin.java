package com.zpkdxgames.plexonbackpacks;

import com.zpkdxgames.plexonbackpacks.command.BackpackCommand;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.listener.BackpackListener;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.recipe.RecipeRegistry;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import com.zpkdxgames.plexonbackpacks.storage.BackpackDataStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlexonBackpacksPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private Messages messages;
    private BackpackDataStore dataStore;
    private BackpackItemFactory itemFactory;
    private BackpackService backpackService;
    private RecipeRegistry recipeRegistry;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        try {
            configManager.reload();
        } catch (RuntimeException exception) {
            getLogger().severe("PlexonBackpacks cannot start: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new Messages(this);
        dataStore = new BackpackDataStore(this);
        dataStore.load();
        itemFactory = new BackpackItemFactory(this, configManager);
        backpackService = new BackpackService(configManager, messages, itemFactory, dataStore);
        recipeRegistry = new RecipeRegistry(this, configManager, itemFactory, messages);

        getServer().getPluginManager().registerEvents(
                new BackpackListener(backpackService, itemFactory, messages),
                this
        );
        getServer().getPluginManager().registerEvents(recipeRegistry, this);

        PluginCommand command = getCommand("backpack");
        if (command == null) {
            getLogger().severe("The backpack command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BackpackCommand executor = new BackpackCommand(
                this,
                configManager,
                messages,
                itemFactory,
                backpackService
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        recipeRegistry.registerAll();
        restartAutosave();
        getLogger().info("PlexonBackpacks 1.0.0 enabled with "
                + configManager.tiers().size() + " tier(s).");
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
        if (dataStore != null) {
            dataStore.flushSync();
        }
    }

    public boolean reloadPlugin() {
        reloadConfig();
        try {
            configManager.reload();
        } catch (RuntimeException exception) {
            getLogger().severe("Could not reload config.yml: " + exception.getMessage());
            return false;
        }

        backpackService.closeAll();
        recipeRegistry.registerAll();
        restartAutosave();
        return true;
    }

    public void saveBackpacksNow() {
        backpackService.snapshotOpenSessions();
        dataStore.saveSync();
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
}
