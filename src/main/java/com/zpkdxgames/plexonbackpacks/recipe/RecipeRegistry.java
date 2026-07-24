package com.zpkdxgames.plexonbackpacks.recipe;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.RecipeDefinition;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class RecipeRegistry implements Listener {
    private final PlexonBackpacksPlugin plugin;
    private final ConfigManager config;
    private final BackpackItemFactory itemFactory;
    private final Messages messages;
    private final Map<NamespacedKey, TierDefinition> tiersByRecipe = new HashMap<>();
    private final Set<NamespacedKey> registeredKeys = new LinkedHashSet<>();

    public RecipeRegistry(
            PlexonBackpacksPlugin plugin,
            ConfigManager config,
            BackpackItemFactory itemFactory,
            Messages messages
    ) {
        this.plugin = plugin;
        this.config = config;
        this.itemFactory = itemFactory;
        this.messages = messages;
    }

    public void registerAll() {
        unregisterAll();
        for (TierDefinition tier : config.tiers()) {
            RecipeDefinition definition = tier.recipe();
            if (!definition.enabled()) {
                continue;
            }

            NamespacedKey key = new NamespacedKey(plugin, "backpack_" + tier.id());
            ShapedRecipe recipe = new ShapedRecipe(key, itemFactory.create(tier, null));
            recipe.shape(definition.shape().toArray(String[]::new));
            definition.ingredients().forEach(recipe::setIngredient);

            if (Bukkit.addRecipe(recipe)) {
                tiersByRecipe.put(key, tier);
                registeredKeys.add(key);
            } else {
                plugin.getLogger().warning("Could not register recipe for tier '" + tier.id() + "'.");
            }
        }
        discoverForOnlinePlayers();
    }

    public void unregisterAll() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
        tiersByRecipe.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        TierDefinition tier = tierFor(event.getRecipe());
        if (tier == null) {
            return;
        }
        event.getInventory().setResult(itemFactory.create(tier, null));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        TierDefinition tier = tierFor(event.getRecipe());
        if (tier == null) {
            return;
        }
        HumanEntity crafter = event.getWhoClicked();
        boolean lacksTierPermission = !tier.permission().isBlank() && !crafter.hasPermission(tier.permission());
        if (!crafter.hasPermission("plexonbackpacks.craft") || lacksTierPermission) {
            event.setCancelled(true);
            messages.send(crafter, "craft-no-permission");
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            messages.send(crafter, "crafted-one-at-a-time");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (config.discoverRecipes()) {
            discover(event.getPlayer());
        }
    }

    private TierDefinition tierFor(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }
        return tiersByRecipe.get(keyed.getKey());
    }

    private void discoverForOnlinePlayers() {
        if (!config.discoverRecipes()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            discover(player);
        }
    }

    private void discover(Player player) {
        for (Map.Entry<NamespacedKey, TierDefinition> entry : tiersByRecipe.entrySet()) {
            TierDefinition tier = entry.getValue();
            if (player.hasPermission("plexonbackpacks.craft")
                    && (tier.permission().isBlank() || player.hasPermission(tier.permission()))) {
                player.discoverRecipe(entry.getKey());
            }
        }
    }
}
