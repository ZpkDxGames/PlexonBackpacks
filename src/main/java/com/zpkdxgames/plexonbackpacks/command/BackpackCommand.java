package com.zpkdxgames.plexonbackpacks.command;

import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin;
import com.zpkdxgames.plexonbackpacks.PlexonBackpacksPlugin.DiagnosticsSnapshot;
import com.zpkdxgames.plexonbackpacks.config.ConfigManager;
import com.zpkdxgames.plexonbackpacks.item.BackpackItemFactory;
import com.zpkdxgames.plexonbackpacks.message.Messages;
import com.zpkdxgames.plexonbackpacks.model.BackpackRecord;
import com.zpkdxgames.plexonbackpacks.model.TierDefinition;
import com.zpkdxgames.plexonbackpacks.service.AdminMenuService;
import com.zpkdxgames.plexonbackpacks.service.BackpackService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final PlexonBackpacksPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final BackpackItemFactory itemFactory;
    private final BackpackService service;
    private final AdminMenuService adminMenu;

    public BackpackCommand(
            PlexonBackpacksPlugin plugin,
            ConfigManager config,
            Messages messages,
            BackpackItemFactory itemFactory,
            BackpackService service,
            AdminMenuService adminMenu
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.itemFactory = itemFactory;
        this.service = service;
        this.adminMenu = adminMenu;
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
            case "inspect" -> inspect(sender);
            case "gui", "admin" -> openAdminMenu(sender);
            case "give" -> give(sender, args);
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

    private void listTiers(CommandSender sender) {
        messages.send(sender, "tiers-header");
        for (TierDefinition tier : config.tiers()) {
            messages.send(
                    sender,
                    "tier-line",
                    "tier", itemFactory.plainTierName(tier),
                    "slots", Integer.toString(tier.slots())
            );
        }
    }

    private boolean inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        ItemStack item = heldBackpack(player).orElse(null);
        if (item == null) {
            messages.send(player, "held-required");
            return true;
        }

        UUID id = itemFactory.backpackId(item).orElseThrow();
        String tierId = itemFactory.tierId(item).orElse("unknown");
        TierDefinition tier = config.tier(tierId).orElse(null);
        BackpackRecord record = service.record(id).orElse(null);
        UUID owner = record == null ? itemFactory.owner(item).orElse(null) : record.owner();
        int slots = tier == null ? 0 : tier.slots();
        messages.send(
                player,
                "inspect",
                "id", id.toString(),
                "tier", tier == null ? tierId : itemFactory.plainTierName(tier),
                "owner", itemFactory.ownerName(owner),
                "slots", Integer.toString(slots)
        );
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

        boolean dropped = false;
        for (int count = 0; count < amount; count++) {
            ItemStack backpack = service.createBackpack(tier);
            if (!target.getInventory().addItem(backpack).isEmpty()) {
                target.getWorld().dropItemNaturally(target.getLocation(), backpack);
                dropped = true;
            }
        }

        messages.send(
                sender,
                "gave",
                "amount", Integer.toString(amount),
                "tier", itemFactory.plainTierName(tier),
                "player", target.getName()
        );
        messages.send(
                target,
                "received",
                "amount", Integer.toString(amount),
                "tier", itemFactory.plainTierName(tier)
        );
        if (dropped) {
            messages.send(target, "dropped-overflow");
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("plexonbackpacks.reload")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (plugin.reloadPlugin()) {
            messages.send(sender, "reloaded");
        } else {
            messages.send(sender, "reload-failed");
        }
        return true;
    }

    private boolean save(CommandSender sender) {
        if (!sender.hasPermission("plexonbackpacks.save")) {
            messages.send(sender, "no-permission");
            return true;
        }
        plugin.saveBackpacksNow();
        messages.send(sender, "saved");
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
        sender.sendMessage("§7CSV compaction threshold: §f" + d.compactionThreshold());
        sender.sendMessage("§7Public API: §f" + (d.publicApiRegistered() ? "REGISTERED" : "NOT_REGISTERED"));
        sender.sendMessage("§7Events: §fOPEN / CLOSE / BIND §8(primary-thread)");
        sender.sendMessage("§8§m----------------------------------------");
        return true;
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
            options.addAll(List.of("open", "tiers", "inspect", "help"));
            if (sender.hasPermission("plexonbackpacks.admin-gui")) {
                options.add("gui");
            }
            if (sender.hasPermission("plexonbackpacks.give")) {
                options.add("give");
            }
            if (sender.hasPermission("plexonbackpacks.reload")) {
                options.add("reload");
            }
            if (sender.hasPermission("plexonbackpacks.save")) {
                options.add("save");
            }
            if (sender.hasPermission("plexonbackpacks.diagnostics")) {
                options.add("diagnostics");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(options::add);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            config.tiers().stream().map(TierDefinition::id).forEach(options::add);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("1", "2", "4", "8"));
        }

        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
