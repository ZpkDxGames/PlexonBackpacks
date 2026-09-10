package com.zpkdxgames.plexonbackpacks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonbackpacks.api.PlexonBackpacksAPI;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackHolder;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
        UUID id = openBasicBackpack(owner);

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
        UUID id = openBasicBackpack(owner);

        server.dispatchCommand(admin, "backpack forceclose " + id + " confirm");
        assertTrue(api.openSessionForBackpack(id).isPresent());

        server.dispatchCommand(admin, "backpack forceclose " + id + " confirm");
        assertTrue(api.openSessionForBackpack(id).isEmpty());
    }

    @Test
    void registryPresentAndHolderIndexPresentClosesNormally() throws Exception {
        Player owner = server.addPlayer();
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();

        assertTrue(service.forceClose(id));
        assertTrue(api.openSessionForBackpack(id).isEmpty());
        assertTrue(service.lastForceCloseDiagnostic().startsWith("REDISCOVERED_LIVE_VIEW_CLOSED"));
    }

    @Test
    void registryPresentHolderIndexMissingAndLiveMatchingGuiIsRediscovered() throws Exception {
        Player owner = server.addPlayer();
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();
        holderIndex(service).remove(owner.getUniqueId());

        assertTrue(api.openSessionForBackpack(id).isPresent());
        assertTrue(owner.getOpenInventory().getTopInventory().getHolder() instanceof BackpackHolder);
        assertTrue(service.forceClose(id));
        assertTrue(api.openSessionForBackpack(id).isEmpty());
        assertTrue(service.lastForceCloseDiagnostic().startsWith("REDISCOVERED_LIVE_VIEW_CLOSED"));
    }

    @Test
    void registryPresentHolderIndexMissingAndNoLiveGuiReleasesOnlyStaleRegistry() throws Exception {
        Player owner = server.addPlayer();
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();
        holderIndex(service).remove(owner.getUniqueId());
        owner.closeInventory();

        assertTrue(api.openSessionForBackpack(id).isPresent());
        assertTrue(service.forceClose(id));
        assertTrue(api.openSessionForBackpack(id).isEmpty());
        assertTrue(service.lastForceCloseDiagnostic().startsWith("STALE_REGISTRY_RELEASED"));
    }

    @Test
    void staleUnrelatedInventoryIsNeverClosed() throws Exception {
        Player owner = server.addPlayer();
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();
        holderIndex(service).remove(owner.getUniqueId());
        owner.closeInventory();

        Inventory unrelated = Bukkit.createInventory(owner, 9);
        owner.openInventory(unrelated);
        assertTrue(service.forceClose(id));
        assertEquals(unrelated, owner.getOpenInventory().getTopInventory());
        assertTrue(api.openSessionForBackpack(id).isEmpty());
    }

    @Test
    void forceCloseMatchesBackpackUuidOnlyAndLeavesOtherBackpackOpen() throws Exception {
        Player targetOwner = server.addPlayer();
        Player otherOwner = server.addPlayer();
        UUID targetId = openBasicBackpack(targetOwner);
        UUID otherId = openBasicBackpack(otherOwner);
        BackpackService service = backpackService();
        holderIndex(service).remove(targetOwner.getUniqueId());
        Inventory otherInventory = otherOwner.getOpenInventory().getTopInventory();

        assertTrue(service.forceClose(targetId));
        assertTrue(api.openSessionForBackpack(targetId).isEmpty());
        assertTrue(api.openSessionForBackpack(otherId).isPresent());
        assertEquals(otherInventory, otherOwner.getOpenInventory().getTopInventory());
    }

    @Test
    void cursorItemSurvivesRediscoveredCloseExactly() throws Exception {
        Player owner = server.addPlayer();
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();
        holderIndex(service).remove(owner.getUniqueId());

        ItemStack cursor = new ItemStack(Material.DIAMOND, 7);
        ItemMeta meta = cursor.getItemMeta();
        meta.displayName(Component.text("force-close cursor custody"));
        cursor.setItemMeta(meta);
        ItemStack expected = cursor.clone();
        owner.setItemOnCursor(cursor);

        assertTrue(service.forceClose(id));
        assertEquals(expected, owner.getItemOnCursor());
        assertTrue(api.openSessionForBackpack(id).isEmpty());
    }

    @Test
    void repeatedConfirmedForceCloseIsIdempotentAndCannotDuplicateCleanup() throws Exception {
        Player owner = server.addPlayer();
        Player admin = server.addPlayer();
        admin.setOp(true);
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();

        confirmForceClose(admin, id);
        assertTrue(api.openSessionForBackpack(id).isEmpty());
        assertTrue(service.lastForceCloseDiagnostic().startsWith("REDISCOVERED_LIVE_VIEW_CLOSED"));

        confirmForceClose(admin, id);
        assertTrue(api.openSessionForBackpack(id).isEmpty());
        assertTrue(service.lastForceCloseDiagnostic().startsWith("NO_ACTIVE_SESSION"));
    }

    @Test
    void forceCloseAfterLogoutCloseRaceReleasesOnlyRemainingStaleRegistry() throws Exception {
        Player owner = server.addPlayer();
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();
        holderIndex(service).remove(owner.getUniqueId());

        owner.closeInventory();
        assertTrue(api.openSessionForBackpack(id).isPresent());
        assertTrue(service.forceClose(id));
        assertTrue(api.openSessionForBackpack(id).isEmpty());
        assertTrue(service.lastForceCloseDiagnostic().startsWith("STALE_REGISTRY_RELEASED"));
    }

    @Test
    void registryReleaseOccursAfterSuccessfulLiveViewInvalidation() throws Exception {
        Player owner = server.addPlayer();
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();
        holderIndex(service).remove(owner.getUniqueId());
        AtomicBoolean matchingCloseObserved = new AtomicBoolean(false);
        AtomicBoolean registryStillHeldInsideCloseEvent = new AtomicBoolean(false);

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onClose(InventoryCloseEvent event) {
                if (event.getInventory().getHolder() instanceof BackpackHolder holder
                        && holder.backpackId().equals(id)) {
                    matchingCloseObserved.set(true);
                    registryStillHeldInsideCloseEvent.set(api.openSessionForBackpack(id).isPresent());
                }
            }
        }, plugin);

        assertTrue(service.forceClose(id));
        assertTrue(matchingCloseObserved.get());
        assertTrue(registryStillHeldInsideCloseEvent.get());
        assertTrue(api.openSessionForBackpack(id).isEmpty());
    }

    @Test
    void mismatchedLiveHolderFailsClosedWithoutRegistryRelease() throws Exception {
        Player owner = server.addPlayer();
        UUID id = openBasicBackpack(owner);
        BackpackService service = backpackService();
        holderIndex(service).remove(owner.getUniqueId());
        owner.closeInventory();
        assertTrue(api.openSessionForBackpack(id).isPresent());

        BackpackHolder forged = new BackpackHolder(id, owner.getUniqueId(), "basic", UUID.randomUUID(), 9);
        Inventory forgedInventory = Bukkit.createInventory(forged, 18);
        forged.inventory(forgedInventory);
        owner.openInventory(forgedInventory);

        assertFalse(service.forceClose(id));
        assertTrue(api.openSessionForBackpack(id).isPresent());
        assertEquals(forgedInventory, owner.getOpenInventory().getTopInventory());
        assertTrue(service.lastForceCloseDiagnostic().startsWith("FAILED_AMBIGUOUS_LIVE_VIEW"));
    }

    private UUID openBasicBackpack(Player owner) {
        ItemStack backpack = api.create("basic");
        UUID id = api.backpackId(backpack).orElseThrow();
        owner.getInventory().setItemInMainHand(backpack);
        assertTrue(api.open(owner, backpack));
        return id;
    }

    private void confirmForceClose(Player admin, UUID id) {
        server.dispatchCommand(admin, "backpack forceclose " + id);
        server.dispatchCommand(admin, "backpack forceclose " + id + " confirm");
    }

    private BackpackService backpackService() throws Exception {
        Field field = PlexonBackpacksPlugin.class.getDeclaredField("backpackService");
        field.setAccessible(true);
        return (BackpackService) field.get(plugin);
    }

    private Map<UUID, BackpackHolder> holderIndex(BackpackService service) throws Exception {
        Field holdersField = BackpackService.class.getDeclaredField("holdersByPlayer");
        holdersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, BackpackHolder> holders = (Map<UUID, BackpackHolder>) holdersField.get(service);
        return holders;
    }
}
