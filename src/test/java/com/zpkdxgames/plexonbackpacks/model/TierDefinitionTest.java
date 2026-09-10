package com.zpkdxgames.plexonbackpacks.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TierDefinitionTest {
    private static final RecipeDefinition NO_RECIPE = new RecipeDefinition(false, List.of(), Map.of());

    @Test
    void acceptsFiniteNonNegativeUpgradeCost() {
        TierDefinition tier = new TierDefinition(
                "iron", "Iron", "Iron", List.of(), 18, "", null, "", 5000.0D, NO_RECIPE);
        assertEquals(5000.0D, tier.upgradeCost());
    }

    @Test
    void rejectsNegativeUpgradeCost() {
        assertThrows(IllegalArgumentException.class, () -> new TierDefinition(
                "iron", "Iron", "Iron", List.of(), 18, "", null, "", -1.0D, NO_RECIPE));
    }

    @Test
    void rejectsInfiniteUpgradeCost() {
        assertThrows(IllegalArgumentException.class, () -> new TierDefinition(
                "iron", "Iron", "Iron", List.of(), 18, "", null, "", Double.POSITIVE_INFINITY, NO_RECIPE));
    }
}
