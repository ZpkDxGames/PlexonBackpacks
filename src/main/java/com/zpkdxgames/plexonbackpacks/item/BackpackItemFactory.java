package com.zpkdxgames.plexonbackpacks.item;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BackpackItemFactory {
    private static final Pattern TEXTURE_URL_PATTERN =
            Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");

    private final PlexonBackpacksPlugin plugin;
    private final ConfigManager config;
    private final NamespacedKey backpackIdKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey ownerKey;
    private final Map<String, ResolvableProfile> profileCache = new ConcurrentHashMap<>();

    public BackpackItemFactory(PlexonBackpacksPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
        this.tierKey = new NamespacedKey(plugin, "tier");
        this.ownerKey = new NamespacedKey(plugin, "owner");
    }

    public ItemStack create(TierDefinition tier, UUID owner) {
        return create(tier, UUID.randomUUID(), owner);
    }

    public ItemStack create(TierDefinition tier, UUID id, UUID owner) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        applyMetadata(item, tier, id, owner);
        return item;
    }

    public ItemStack createMenuIcon(TierDefinition tier, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        applyMetadata(item, tier, null, null);
        ItemMeta meta = item.getItemMeta();
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createPlayerProfileHead(
            UUID profileId,
            String profileName,
            Component displayName,
            List<Component> lore
    ) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        item.setData(
                DataComponentTypes.PROFILE,
                ResolvableProfile.resolvableProfile().uuid(profileId).name(profileName)
        );
        return item;
    }

    public void refresh(ItemStack item, TierDefinition tier, UUID owner) {
        backpackId(item).ifPresent(id -> applyMetadata(item, tier, id, owner));
    }

    private void applyMetadata(ItemStack item, TierDefinition tier, UUID id, UUID owner) {
        ItemMeta meta = item.getItemMeta();

        meta.displayName(config.component(
                tier.displayName(),
                "tier", plainTierName(tier),
                "slots", Integer.toString(tier.slots()),
                "owner", ownerName(owner)
        ));

        List<Component> lore = new ArrayList<>();
        for (String line : tier.lore()) {
            lore.add(config.component(
                    line,
                    "tier", plainTierName(tier),
                    "slots", Integer.toString(tier.slots()),
                    "owner", ownerName(owner)
            ));
        }
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (id == null) {
            data.remove(backpackIdKey);
            data.remove(tierKey);
            data.remove(ownerKey);
        } else {
            data.set(backpackIdKey, PersistentDataType.STRING, id.toString());
            data.set(tierKey, PersistentDataType.STRING, tier.id());
            if (owner == null) {
                data.remove(ownerKey);
            } else {
                data.set(ownerKey, PersistentDataType.STRING, owner.toString());
            }
        }

        item.setItemMeta(meta);
        applyTexture(item, tier.texture());
        if (tier.customModelData() != null && tier.customModelData() > 0) {
            item.setData(
                    DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addFloat(tier.customModelData().floatValue())
            );
        } else {
            item.unsetData(DataComponentTypes.CUSTOM_MODEL_DATA);
        }
        item.setAmount(1);
    }

    private void applyTexture(ItemStack item, String configuredTexture) {
        Optional<URL> textureUrl = parseTextureUrl(configuredTexture);
        if (textureUrl.isEmpty()) {
            item.unsetData(DataComponentTypes.PROFILE);
            return;
        }

        String key = textureUrl.get().toString();
        ResolvableProfile profile = profileCache.computeIfAbsent(key, ignored -> {
            UUID profileId = UUID.nameUUIDFromBytes(
                    ("PlexonBackpacks:" + textureUrl.get()).getBytes(StandardCharsets.UTF_8)
            );
            String textureJson = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl.get() + "\"}}}";
            String textureValue = Base64.getEncoder()
                    .encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
            return ResolvableProfile.resolvableProfile()
                    .uuid(profileId)
                    .name("PlexonPack")
                    .addProperty(new ProfileProperty("textures", textureValue))
                    .build();
        });
        item.setData(DataComponentTypes.PROFILE, profile);
    }

    private Optional<URL> parseTextureUrl(String configuredTexture) {
        if (configuredTexture == null || configuredTexture.isBlank()) {
            return Optional.empty();
        }

        String value = configuredTexture.trim();
        String resolved;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            resolved = value;
        } else if (value.matches("[a-fA-F0-9]{32,128}")) {
            resolved = "https://textures.minecraft.net/texture/" + value;
        } else {
            try {
                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                Matcher matcher = TEXTURE_URL_PATTERN.matcher(decoded);
                if (!matcher.find()) {
                    plugin.getLogger().warning("A backpack texture value does not contain a valid URL.");
                    return Optional.empty();
                }
                resolved = matcher.group(1).replace("\\/", "/");
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("A backpack texture is not a hash, URL, or valid base64 value.");
                return Optional.empty();
            }
        }

        try {
            return Optional.of(URI.create(resolved).toURL());
        } catch (IllegalArgumentException | MalformedURLException exception) {
            plugin.getLogger().warning("Invalid backpack texture URL: " + resolved);
            return Optional.empty();
        }
    }

    public boolean isBackpack(ItemStack item) {
        return backpackId(item).isPresent() && tierId(item).isPresent();
    }

    public Optional<UUID> backpackId(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(backpackIdKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<String> tierId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer()
                .get(tierKey, PersistentDataType.STRING));
    }

    public Optional<UUID> owner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(ownerKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String ownerName(UUID owner) {
        if (owner == null) {
            return "Unbound";
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(owner);
        String name = offlinePlayer.getName();
        return name == null ? owner.toString().substring(0, 8) : name;
    }

    public String plainTierName(TierDefinition tier) {
        String id = tier.id();
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
