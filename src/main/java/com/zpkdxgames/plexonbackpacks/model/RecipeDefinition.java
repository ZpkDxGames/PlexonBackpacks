package com.zpkdxgames.plexonbackpacks.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record RecipeDefinition(
        boolean enabled,
        List<String> shape,
        Map<Character, Material> ingredients
) {
}
