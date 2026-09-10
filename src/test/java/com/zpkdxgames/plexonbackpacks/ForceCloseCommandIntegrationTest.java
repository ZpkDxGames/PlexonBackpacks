package com.zpkdxgames.plexonbackpacks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.api.PlexonBackpacksAPI;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class ForceCloseCommandIntegrationTest {
    private ServerMock server;
    private PlexonBackpacksPlugin plugin;
    private PlexonBackpacksAPI api;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PlexonBackpacksPlugin.class);
        api = Bukkit.getServicesManager().load(PlexonBackpacksAPI.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void activeOwnerSessionRequiresTwoSeparateCommands() {
        Player owner = server.addPlayer();
        Player admin = server.addPlayer();
        admin.setOp(true);
        ItemStack backpack = api.create("basic");
        UUID id = api.backpackId(backpack).orElseThrow();
        owner.getInventory().setItemInMainHand(backpack);
        assertTrue(api.open(owner, backpack));

        server.dispatchCommand(admin, "backpack forceclose " + id);
        assertTrue(api.openSessionForBackpack(id).isPresent());

        server.dispatchCommand(admin, "backpack forceclose " + id + " confirm");
        assertTrue(api.openSessionForBackpack(id).isEmpty());
    }

    @Test
    void confirmCannotBypassTheStagingCommand() {
        Player owner = server.addPlayer();
        Player admin = server.addPlayer();
        admin.setOp(true);
        ItemStack backpack = api.create("basic");
        UUID id = api.backpackId(backpack).orElseThrow();
        owner.getInventory().setItemInMainHand(backpack);
        assertTrue(api.open(owner, backpack));

        server.dispatchCommand(admin, "backpack forceclose " + id + " confirm");
        assertTrue(api.openSessionForBackpack(id).isPresent());

        server.dispatchCommand(admin, "backpack forceclose " + id + " confirm");
        assertTrue(api.openSessionForBackpack(id).isEmpty());
    }

    @Test
    void confirmedForceCloseReleasesAStaleRegistrySession() throws Exception {
        Player owner = server.addPlayer();
        Player admin = server.addPlayer();
        admin.setOp(true);
        ItemStack backpack = api.create("basic");
        UUID id = api.backpackId(backpack).orElseThrow();
        owner.getInventory().setItemInMainHand(backpack);
        assertTrue(api.open(owner, backpack));

        BackpackService service = backpackService();
        Field holdersField = BackpackService.class.getDeclaredField("holdersByPlayer");
        holdersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, ?> holders = (Map<UUID, ?>) holdersField.get(service);
        holders.remove(owner.getUniqueId());
        owner.closeInventory();
        assertTrue(api.openSessionForBackpack(id).isPresent());

        server.dispatchCommand(admin, "backpack forceclose " + id);
        assertTrue(api.openSessionForBackpack(id).isPresent());
        server.dispatchCommand(admin, "backpack forceclose " + id + " confirm");
        assertTrue(api.openSessionForBackpack(id).isEmpty());
    }

    private BackpackService backpackService() throws Exception {
        Field field = PlexonBackpacksPlugin.class.getDeclaredField("backpackService");
        field.setAccessible(true);
        return (BackpackService) field.get(plugin);
    }
}
