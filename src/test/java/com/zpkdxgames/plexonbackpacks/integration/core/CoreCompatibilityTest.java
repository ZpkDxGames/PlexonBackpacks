package com.zpkdxgames.plexonbackpacks.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCompatibilityTest {
    @Test
    void acceptsCoreOneAndTwoAndRejectsCoreThree() {
        ModuleVersionRange range = ModuleVersionRange.parse(CoreBridge.SUPPORTED_API_RANGE);
        assertTrue(range.contains(CoreVersion.of(1, 0, "1.0.0")));
        assertTrue(range.contains(CoreVersion.of(1, 99, "1.x")));
        assertTrue(range.contains(CoreVersion.of(2, 0, "2.0.4")));
        assertFalse(range.contains(CoreVersion.of(3, 0, "3.0.0")));
    }
}
