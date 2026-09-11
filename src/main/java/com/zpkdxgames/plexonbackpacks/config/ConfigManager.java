package com.zpkdxgames.plexonbackpacks.config;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.model.RecipeDefinition;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ConfigManager {
    private final PlexonBackpacksPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private Map<String, TierDefinition> tiers = Map.of();

    public ConfigManager(PlexonBackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Map<String, TierDefinition> loaded = new LinkedHashMap<>();
        ConfigurationSection tierSection = plugin.getConfig().getConfigurationSection("tiers");
        if (tierSection == null) {
            throw new IllegalStateException("No tiers are configured in config.yml");
        }

        for (String rawId : tierSection.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection section = tierSection.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }

            int slots = section.getInt("slots", 9);
            if (slots < 9 || slots > 54 || slots % 9 != 0) {
                plugin.getLogger().warning("Skipping tier '" + id
                        + "': slots must be a multiple of 9 between 9 and 54.");
                continue;
            }

            String displayName = section.getString("display-name", "<white>" + id);
            String inventoryTitle = section.getString("inventory-title", displayName);
            List<String> lore = section.getStringList("lore");
            String texture = section.getString("texture", "");
            Integer customModelData = section.contains("custom-model-data")
                    ? section.getInt("custom-model-data")
                    : null;
            String permission = section.getString("permission", "");
            double upgradeCost = section.getDouble("upgrade-cost", 0.0D);
            if (!Double.isFinite(upgradeCost) || upgradeCost < 0.0D) {
                throw new IllegalStateException("Tier '" + id + "' has an invalid upgrade-cost");
            }
            RecipeDefinition recipe = readRecipe(id, section.getConfigurationSection("recipe"));

            loaded.put(id, new TierDefinition(
                    id,
                    displayName,
                    inventoryTitle,
                    List.copyOf(lore),
                    slots,
                    texture,
                    customModelData,
                    permission == null ? "" : permission,
                    upgradeCost,
                    recipe
            ));
        }

        if (loaded.isEmpty()) {
            throw new IllegalStateException("No valid backpack tiers were found in config.yml");
        }
        tiers = Collections.unmodifiableMap(loaded);
    }

    private RecipeDefinition readRecipe(String tierId, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return new RecipeDefinition(false, List.of(), Map.of());
        }

        List<String> shape = section.getStringList("shape");
        int expectedWidth = shape.isEmpty() ? 0 : shape.getFirst().length();
        if (shape.isEmpty()
                || shape.size() > 3
                || expectedWidth == 0
                || expectedWidth > 3
                || shape.stream().anyMatch(row -> row.length() != expectedWidth)) {
            plugin.getLogger().warning("Recipe for tier '" + tierId + "' has an invalid shape; disabling it.");
            return new RecipeDefinition(false, List.of(), Map.of());
        }

        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            plugin.getLogger().warning("Recipe for tier '" + tierId + "' has no ingredients; disabling it.");
            return new RecipeDefinition(false, List.of(), Map.of());
        }

        for (String key : ingredientsSection.getKeys(false)) {
            if (key.length() != 1) {
                plugin.getLogger().warning("Ignoring invalid recipe key '" + key + "' for tier '" + tierId + "'.");
                continue;
            }
            Material material = Material.matchMaterial(ingredientsSection.getString(key, ""));
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("Ignoring invalid material for key '" + key
                        + "' in tier '" + tierId + "'.");
                continue;
            }
            ingredients.put(key.charAt(0), material);
        }

        boolean allKeysDefined = shape.stream()
                .flatMapToInt(String::chars)
                .filter(character -> character != ' ')
                .allMatch(character -> ingredients.containsKey((char) character));
        if (!allKeysDefined) {
            plugin.getLogger().warning("Recipe for tier '" + tierId
                    + "' uses a character without an ingredient; disabling it.");
            return new RecipeDefinition(false, List.of(), Map.of());
        }

        return new RecipeDefinition(true, List.copyOf(shape), Map.copyOf(ingredients));
    }

    public Optional<TierDefinition> tier(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tiers.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<TierDefinition> tiers() {
        return List.copyOf(tiers.values());
    }

    public Optional<TierDefinition> nextTier(String currentTierId) {
        List<TierDefinition> ordered = tiers();
        for (int index = 0; index < ordered.size(); index++) {
            if (!ordered.get(index).id().equalsIgnoreCase(currentTierId)) {
                continue;
            }
            return index + 1 < ordered.size() ? Optional.of(ordered.get(index + 1)) : Optional.empty();
        }
        return Optional.empty();
    }

    public boolean ownershipEnabled() {
        return plugin.getConfig().getBoolean("settings.ownership-enabled", true);
    }

    public boolean bindOnFirstOpen() {
        return plugin.getConfig().getBoolean("settings.bind-on-first-open", true);
    }

    public boolean discoverRecipes() {
        return plugin.getConfig().getBoolean("settings.discover-recipes", true);
    }

    public boolean upgradesEnabled() {
        return plugin.getConfig().getBoolean("settings.upgrades-enabled", true);
    }

    public boolean quickDepositEnabled() {
        return plugin.getConfig().getBoolean("settings.quick-deposit-enabled", true);
    }

    public long autosaveIntervalTicks() {
        return Math.max(0L, plugin.getConfig().getLong("settings.autosave-interval-ticks", 200L));
    }

    public long csvCompactionThresholdUpdates() {
        return Math.max(
                1_000L,
                plugin.getConfig().getLong("settings.csv-compaction-threshold-updates", 10_000L)
        );
    }

    public Component component(String value, String... placeholders) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            resolvers.add(Placeholder.unparsed(placeholders[index], placeholders[index + 1]));
        }
        return miniMessage.deserialize(value, TagResolver.resolver(resolvers))
                .decoration(TextDecoration.ITALIC, false);
    }
}
