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
        double upgradeCost,
        RecipeDefinition recipe
) {
    public TierDefinition {
        if (upgradeCost < 0.0D || !Double.isFinite(upgradeCost)) {
            throw new IllegalArgumentException("upgradeCost must be finite and non-negative");
        }
    }
}
