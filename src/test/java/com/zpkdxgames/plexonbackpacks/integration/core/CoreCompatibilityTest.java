package com.zpkdxgames.plexonbackpacks.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCompatibilityTest {
    @Test
    void acceptsCoreOneAndRejectsCoreTwo() {
        ModuleVersionRange range = ModuleVersionRange.parse(CoreBridge.SUPPORTED_API_RANGE);
        assertTrue(range.contains(CoreVersion.of(1, 0, "1.0.0")));
        assertTrue(range.contains(CoreVersion.of(1, 99, "1.x")));
        assertFalse(range.contains(CoreVersion.of(2, 0, "2.0.0")));
    }
}
