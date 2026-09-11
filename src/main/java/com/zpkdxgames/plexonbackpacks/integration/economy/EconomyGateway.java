package com.zpkdxgames.plexonbackpacks.integration.economy;

import java.util.UUID;

/**
 * Minimal authoritative economy boundary used by transactional backpack upgrades.
 * Implementations must never keep an independent balance.
 */
public interface EconomyGateway {
    boolean available();

    String providerName();

    boolean has(UUID playerId, double amount);

    boolean withdraw(UUID playerId, double amount);

    boolean refund(UUID playerId, double amount);
}
