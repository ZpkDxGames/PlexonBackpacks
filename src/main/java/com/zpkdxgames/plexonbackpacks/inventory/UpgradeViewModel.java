package com.zpkdxgames.plexonbackpacks.inventory;

import java.util.Locale;
import java.util.Objects;

/** Immutable player-facing summary of the already-authoritative upgrade contract. */
public record UpgradeViewModel(
        String currentTier,
        int currentCapacity,
        String nextTier,
        int nextCapacity,
        double cost,
        Status status
) {
    public UpgradeViewModel {
        currentTier = Objects.requireNonNullElse(currentTier, "Unknown");
        nextTier = Objects.requireNonNullElse(nextTier, "");
        status = Objects.requireNonNull(status, "status");
        if (currentCapacity < 0 || nextCapacity < 0 || !Double.isFinite(cost) || cost < 0.0D) {
            throw new IllegalArgumentException("invalid upgrade presentation values");
        }
    }

    public boolean ready() {
        return status == Status.READY;
    }

    public String formattedCost() {
        if (cost == Math.rint(cost)) {
            return String.format(Locale.ROOT, "%,.0f", cost);
        }
        return String.format(Locale.ROOT, "%,.2f", cost);
    }

    public enum Status {
        READY,
        MAX_TIER,
        UPGRADES_DISABLED,
        WRONG_OWNER,
        MISSING_REQUIREMENT,
        ECONOMY_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        BACKPACK_OPEN,
        REFERENCE_MISSING
    }
}
