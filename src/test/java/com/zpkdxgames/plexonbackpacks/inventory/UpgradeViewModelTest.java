package com.zpkdxgames.plexonbackpacks.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpgradeViewModelTest {
    @Test
    void readyUpgradeShowsExactTierCapacityAndCost() {
        UpgradeViewModel view = new UpgradeViewModel(
                "Iron Backpack", 18, "Gold Backpack", 27, 15000.0D, UpgradeViewModel.Status.READY);

        assertTrue(view.ready());
        assertEquals("15,000", view.formattedCost());
        assertEquals(18, view.currentCapacity());
        assertEquals(27, view.nextCapacity());
    }

    @Test
    void maxTierIsNotSubmittable() {
        UpgradeViewModel view = new UpgradeViewModel(
                "Netherite Backpack", 54, "", 54, 0.0D, UpgradeViewModel.Status.MAX_TIER);

        assertFalse(view.ready());
        assertEquals(UpgradeViewModel.Status.MAX_TIER, view.status());
    }

    @Test
    void missingRequirementAndInsufficientFundsRemainBlocked() {
        UpgradeViewModel missing = new UpgradeViewModel(
                "Basic Backpack", 9, "Iron Backpack", 18, 5000.0D,
                UpgradeViewModel.Status.MISSING_REQUIREMENT);
        UpgradeViewModel funds = new UpgradeViewModel(
                "Basic Backpack", 9, "Iron Backpack", 18, 5000.0D,
                UpgradeViewModel.Status.INSUFFICIENT_FUNDS);

        assertFalse(missing.ready());
        assertFalse(funds.ready());
    }
}
