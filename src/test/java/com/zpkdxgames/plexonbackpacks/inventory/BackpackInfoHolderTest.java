package com.zpkdxgames.plexonbackpacks.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BackpackInfoHolderTest {
    @Test
    void remembersOriginAndAcceptsOneUpgradeSubmission() {
        UUID id = UUID.randomUUID();
        BackpackInfoHolder holder = new BackpackInfoHolder(id, "gold", 1);

        assertEquals(id, holder.backpackId());
        assertEquals("gold", holder.expectedTierId());
        assertEquals(1, holder.returnPage());
        assertTrue(holder.trySubmit());
        assertFalse(holder.trySubmit());
        assertFalse(holder.trySubmit());
    }
}
