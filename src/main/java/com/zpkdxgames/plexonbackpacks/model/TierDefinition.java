package com.zpkdxgames.plexonbackpacks.model;

import java.util.List;

public record TierDefinition(
        String id,
        String displayName,
        String inventoryTitle,
        List<String> lore,
        int slots,
        String texture,
        Integer customModelData,
        String permission,
        RecipeDefinition recipe
) {
}
