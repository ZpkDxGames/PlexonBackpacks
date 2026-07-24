package com.zpkdxgames.plexonbackpacks.message;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;

public final class Messages {
    private final PlexonBackpacksPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(PlexonBackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(Audience audience, String key, String... placeholders) {
        audience.sendMessage(component(key, placeholders));
    }

    public Component component(String key, String... placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, "<red>Missing message: " + key);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        List<TagResolver> resolvers = new ArrayList<>();
        resolvers.add(Placeholder.component("prefix", miniMessage.deserialize(prefix)));
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            resolvers.add(Placeholder.unparsed(placeholders[index], placeholders[index + 1]));
        }
        return miniMessage.deserialize(raw, TagResolver.resolver(resolvers))
                .decoration(TextDecoration.ITALIC, false);
    }

    public void sendHelp(Audience audience) {
        List<String> lines = plugin.getConfig().getStringList("messages.help");
        for (String line : lines) {
            audience.sendMessage(miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
    }
}
