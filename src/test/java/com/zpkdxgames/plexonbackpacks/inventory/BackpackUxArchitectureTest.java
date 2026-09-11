package com.zpkdxgames.plexonbackpacks.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BackpackUxArchitectureTest {
    @Test
    void playerStorageRendererDoesNotExposeInternalIdentity() throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/zpkdxgames/plexonbackpacks/inventory/BackpackGuiRenderer.java"));
        String info = Files.readString(Path.of(
                "src/main/java/com/zpkdxgames/plexonbackpacks/inventory/BackpackInfoGui.java"));

        assertFalse(renderer.contains("UUID:"));
        assertFalse(renderer.contains("sessionId"));
        assertFalse(renderer.contains("schema"));
        assertFalse(info.contains("UUID:"));
        assertFalse(info.contains("BackpackDataStore"));
        assertFalse(info.contains("saveSync("));
        assertFalse(info.contains("withdraw("));
        assertFalse(info.contains("refund("));
    }

    @Test
    void presentationAddsNoRepeatingGuiTask() throws Exception {
        String info = Files.readString(Path.of(
                "src/main/java/com/zpkdxgames/plexonbackpacks/inventory/BackpackInfoGui.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/com/zpkdxgames/plexonbackpacks/inventory/BackpackGuiRenderer.java"));

        assertFalse(info.contains("runTaskTimer"));
        assertFalse(renderer.contains("runTaskTimer"));
        assertFalse(info.contains("BukkitTask"));
        assertFalse(renderer.contains("BukkitTask"));
    }

    @Test
    void existingAuthoritiesStillRouteSortDepositAndUpgrade() throws Exception {
        String listener = Files.readString(Path.of(
                "src/main/java/com/zpkdxgames/plexonbackpacks/listener/BackpackListener.java"));
        String info = Files.readString(Path.of(
                "src/main/java/com/zpkdxgames/plexonbackpacks/inventory/BackpackInfoGui.java"));
        String command = Files.readString(Path.of(
                "src/main/java/com/zpkdxgames/plexonbackpacks/command/BackpackCommand.java"));

        assertTrue(listener.contains("service.sort(holder)"));
        assertTrue(listener.contains("service.quickDeposit(player, holder)"));
        assertTrue(info.contains("service.upgrade(player, reference)"));
        assertTrue(command.contains("infoGui.openForReference(player, item)"));
        assertFalse(command.contains("service.upgrade(player, item)"));
    }
}
