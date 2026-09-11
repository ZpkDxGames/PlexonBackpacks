package com.zpkdxgames.plexonbackpacks.command;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin.DiagnosticsSnapshot;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackHolder;
import com.zpkdxgames.plexonbackpacks.inventory.BackpackInfoGui;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.service.AdminMenuService;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BackpackCommand implements CommandExecutor, TabCompleter {
    private static final long FORCE_CLOSE_CONFIRM_WINDOW_MILLIS = 30_000L;

    private final PlexonBackpacksPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final BackpackItemFactory itemFactory;
    private final BackpackService service;
    private final AdminMenuService adminMenu;
    private final BackpackInfoGui infoGui;
    private final Map<String, PendingForceClose> pendingForceCloses = new HashMap<>();

    public BackpackCommand(
            PlexonBackpacksPlugin plugin,
            ConfigManager config,
            Messages messages,
            BackpackItemFactory itemFactory,
            BackpackService service,
            AdminMenuService adminMenu,
            BackpackInfoGui infoGui
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.itemFactory = itemFactory;
        this.service = service;
        this.adminMenu = adminMenu;
        this.infoGui = infoGui;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            return open(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                messages.sendHelp(sender);
                yield true;
            }
            case "tiers" -> {
                listTiers(sender);
                yield true;
            }
            case "inspect" -> inspect(sender, args);
            case "upgrade" -> upgrade(sender);
            case "gui", "admin" -> openAdminMenu(sender);
            case "give" -> give(sender, args);
            case "forceclose" -> forceClose(sender, args);
            case "recover" -> recover(sender, args);
            case "repair" -> repair(sender);
            case "reload" -> reload(sender);
            case "save" -> save(sender);
            case "diagnostics" -> diagnostics(sender);
            default -> {
                messages.sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean open(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        ItemStack item = heldBackpack(player).orElse(null);
        if (item == null) {
            messages.send(player, "held-required");
            return true;
        }
        service.open(player, item);
        return true;
    }

    private boolean upgrade(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("plexonbackpacks.upgrade")) {
            messages.send(player, "no-permission");
            return true;
        }
        ItemStack item = heldBackpack(player).orElse(null);
        if (item == null) {
            messages.send(player, "held-required");
            return true;
        }
        infoGui.openForReference(player, item);
        return true;
    }

    private void listTiers(CommandSender sender) {
        messages.send(sender, "tiers-header");
        for (TierDefinition tier : config.tiers()) {
            messages.send(sender, "tier-line",
                    "tier", itemFactory.plainTierName(tier),
                    "slots", Integer.toString(tier.slots()));
        }
    }

    private boolean inspect(CommandSender sender, String[] args) {
        UUID id;
        if (args.length >= 2) {
            if (!sender.hasPermission("plexonbackpacks.inspect-any")) {
                messages.send(sender, "no-permission");
                return true;
            }
            id = parseUuid(sender, args[1]);
            if (id == null) {
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
                return true;
            }
            ItemStack item = heldBackpack(player).orElse(null);
            if (item == null) {
                messages.send(player, "held-required");
                return true;
            }
            id = itemFactory.backpackId(item).orElseThrow();
        }

        BackpackRecord record = service.record(id).orElse(null);
        if (record == null) {
            messages.send(sender, "missing-state");
            return true;
        }
        TierDefinition tier = config.tier(record.tierId()).orElse(null);
        int slots = tier == null ? record.contents().length : record.requiredSize(tier.slots());
        BackpackHolder session = service.sessionByBackpack(id).orElse(null);
        messages.send(sender, "inspect",
                "id", id.toString(),
                "tier", tier == null ? record.tierId() : itemFactory.plainTierName(tier),
                "owner", itemFactory.ownerName(record.owner()),
                "slots", Integer.toString(slots),
                "session", session == null ? "CLOSED" : "OPEN:" + session.viewerId(),
                "last", Long.toString(record.lastAccess()));
        return true;
    }

    private boolean openAdminMenu(CommandSender sender) {
        if (!sender.hasPermission("plexonbackpacks.admin-gui")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        adminMenu.open(player);
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("plexonbackpacks.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            messages.sendHelp(sender);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return true;
        }
        TierDefinition tier = config.tier(args[2]).orElse(null);
        if (tier == null) {
            messages.send(sender, "invalid-tier", "tier", args[2]);
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                messages.send(sender, "invalid-amount");
                return true;
            }
        }
        if (amount < 1 || amount > 64) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        if (freeStorageSlots(target) < amount) {
            messages.send(sender, "inventory-space", "player", target.getName(), "amount", Integer.toString(amount));
            return true;
        }

        List<ItemStack> created = new ArrayList<>(amount);
        try {
            for (int count = 0; count < amount; count++) {
                created.add(service.createBackpack(tier));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Backpack give aborted before custody transfer: " + exception.getMessage());
            messages.send(sender, "persistence-failed");
            return true;
        }
        for (ItemStack backpack : created) {
            if (!target.getInventory().addItem(backpack).isEmpty()) {
                plugin.getLogger().severe("Unexpected inventory capacity race while giving backpack to "
                        + target.getUniqueId() + "; generated backpack remains persisted for admin recovery.");
                messages.send(sender, "operation-failed");
                return true;
            }
        }

        messages.send(sender, "gave",
                "amount", Integer.toString(amount),
                "tier", itemFactory.plainTierName(tier),
                "player", target.getName());
        messages.send(target, "received",
                "amount", Integer.toString(amount),
                "tier", itemFactory.plainTierName(tier));
        return true;
    }

    private boolean forceClose(CommandSender sender, String[] args) {
        if (!sender.hasPermission("plexonbackpacks.force-close")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "uuid-required");
            return true;
        }
        UUID id = parseUuid(sender, args[1]);
        if (id == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        pendingForceCloses.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
        String senderKey = confirmationKey(sender);
        PendingForceClose pending = pendingForceCloses.get(senderKey);
        boolean explicitConfirm = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        if (!explicitConfirm || pending == null || !pending.backpackId().equals(id)
                || pending.expiresAtMillis() < now) {
            pendingForceCloses.put(senderKey,
                    new PendingForceClose(id, now + FORCE_CLOSE_CONFIRM_WINDOW_MILLIS));
            messages.send(sender, "force-close-confirm", "id", id.toString());
            return true;
        }

        pendingForceCloses.remove(senderKey);
        boolean closed = service.forceClose(id);
        messages.send(sender, closed ? "force-close-success" : "force-close-failed", "id", id.toString());
        return true;
    }

    private boolean recover(CommandSender sender, String[] args) {
        if (!sender.hasPermission("plexonbackpacks.recover")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "uuid-required");
            return true;
        }
        UUID id = parseUuid(sender, args[1]);
        if (id == null) {
            return true;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            messages.send(sender, "recover-confirm", "id", id.toString());
            return true;
        }
        if (freeStorageSlots(player) < 1) {
            messages.send(sender, "inventory-space", "player", player.getName(), "amount", "1");
            return true;
        }
        ItemStack recovered = service.recoverReference(id).orElse(null);
        if (recovered == null) {
            messages.send(sender, "recover-failed", "id", id.toString());
            return true;
        }
        if (!player.getInventory().addItem(recovered).isEmpty()) {
            messages.send(sender, "operation-failed");
            return true;
        }
        messages.send(sender, "recover-success", "id", id.toString());
        return true;
    }

    private boolean repair(CommandSender sender) {
        if (!sender.hasPermission("plexonbackpacks.repair")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        ItemStack item = heldBackpack(player).orElse(null);
        if (item == null) {
            messages.send(player, "held-required");
            return true;
        }
        boolean repaired = service.repairReference(item);
        messages.send(player, repaired ? "repair-success" : "repair-failed");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("plexonbackpacks.reload")) {
            messages.send(sender, "no-permission");
            return true;
        }
        messages.send(sender, plugin.reloadPlugin() ? "reloaded" : "reload-failed");
        return true;
    }

    private boolean save(CommandSender sender) {
        if (!sender.hasPermission("plexonbackpacks.save")) {
            messages.send(sender, "no-permission");
            return true;
        }
        messages.send(sender, plugin.saveBackpacksNow() ? "saved" : "persistence-failed");
        return true;
    }

    private boolean diagnostics(CommandSender sender) {
        if (!sender.hasPermission("plexonbackpacks.diagnostics")) {
            messages.send(sender, "no-permission");
            return true;
        }
        DiagnosticsSnapshot d = plugin.diagnostics();
        sender.sendMessage("§8§m----------------------------------------");
        sender.sendMessage("§6PlexonBackpacks Diagnostics §7v" + d.pluginVersion());
        sender.sendMessage("§7Platform: §f" + d.platformVersion());
        sender.sendMessage("§7Java: §f" + d.javaVersion());
        sender.sendMessage("§7Mode: §f" + d.mode());
        sender.sendMessage("§7Core: §f" + (d.coreInstalled() ? d.corePluginVersion() : "not installed")
                + " §8(API " + d.coreApiVersion() + ", supported " + d.supportedCoreRange() + ")");
        sender.sendMessage("§7Module state: §f" + d.moduleState());
        sender.sendMessage("§7Core detail: §f" + d.coreDetail());
        sender.sendMessage("§7Tiers: §f" + d.tiers() + " §8| §7Records: §f" + d.backpackRecords());
        sender.sendMessage("§7Open sessions: §f" + d.openSessions() + " §8| §7Locks: §f" + d.activeLocks());
        sender.sendMessage("§7Persistence schema: §f" + d.schemaVersion()
                + " §8| §7Dirty: §f" + d.dirtyRecords() + " §8| §7Writer: §f"
                + (d.writerRunning() ? "ACTIVE" : "IDLE"));
        sender.sendMessage("§7Journal rows: §f" + d.journalRows()
                + " §8| §7Compaction threshold: §f" + d.compactionThreshold());
        sender.sendMessage("§7Last persistence failure: §f" + d.lastPersistenceFailure());
        sender.sendMessage("§7Economy: §f" + d.economyProvider());
        sender.sendMessage("§7Public API: §f" + (d.publicApiRegistered() ? "REGISTERED" : "NOT_REGISTERED"));
        sender.sendMessage("§7Publication readiness: §f" + (d.publicationReady() ? "SOURCE-READY" : "BLOCKED"));
        sender.sendMessage("§7Runtime certification: §ePENDING until PlexonCraft gate is executed");
        sender.sendMessage("§8§m----------------------------------------");
        return true;
    }

    private UUID parseUuid(CommandSender sender, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            messages.send(sender, "invalid-uuid", "id", value);
            return null;
        }
    }

    private static String confirmationKey(CommandSender sender) {
        if (sender instanceof Player player) {
            return "player:" + player.getUniqueId();
        }
        return sender.getClass().getName() + ':' + sender.getName();
    }

    private static int freeStorageSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    private Optional<ItemStack> heldBackpack(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (itemFactory.isBackpack(mainHand)) {
            return Optional.of(mainHand);
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (itemFactory.isBackpack(offHand)) {
            return Optional.of(offHand);
        }
        return Optional.empty();
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("open", "tiers", "inspect", "upgrade", "help"));
            if (sender.hasPermission("plexonbackpacks.admin-gui")) options.add("gui");
            if (sender.hasPermission("plexonbackpacks.give")) options.add("give");
            if (sender.hasPermission("plexonbackpacks.force-close")) options.add("forceclose");
            if (sender.hasPermission("plexonbackpacks.recover")) options.add("recover");
            if (sender.hasPermission("plexonbackpacks.repair")) options.add("repair");
            if (sender.hasPermission("plexonbackpacks.reload")) options.add("reload");
            if (sender.hasPermission("plexonbackpacks.save")) options.add("save");
            if (sender.hasPermission("plexonbackpacks.diagnostics")) options.add("diagnostics");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(options::add);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            config.tiers().stream().map(TierDefinition::id).forEach(options::add);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("1", "2", "4", "8"));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("recover")
                || args[0].equalsIgnoreCase("forceclose"))) {
            options.add("confirm");
        }

        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }

    private record PendingForceClose(UUID backpackId, long expiresAtMillis) {
    }
}
