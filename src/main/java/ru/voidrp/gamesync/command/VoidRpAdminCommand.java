package ru.voidrp.gamesync.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import ru.voidrp.gamesync.VoidRpGameSyncPlugin;
import ru.voidrp.gamesync.model.NationDefinition;

public final class VoidRpAdminCommand implements CommandExecutor, TabCompleter {

    private final VoidRpGameSyncPlugin plugin;

    public VoidRpAdminCommand(VoidRpGameSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidrp.gamesync.admin")) {
            sender.sendMessage("§cНет прав.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload" -> {
                plugin.reloadPluginState();
                sender.sendMessage("§aVoidRpGameSync reload completed.");
                return true;
            }
            case "sync" -> {
                handleSync(sender, args);
                return true;
            }
            case "reward" -> {
                handleReward(sender, args);
                return true;
            }
            case "nation" -> {
                handleNation(sender, args);
                return true;
            }
            case "meta" -> {
                handleMeta(sender, args);
                return true;
            }
            case "nations" -> {
                handleNations(sender, args);
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void handleSync(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eИспользование: /vrgs sync <all|nation> [slug]");
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("all")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getNationSyncService().syncAll());
            sender.sendMessage("§aЗапущен sync всех государств.");
            return;
        }

        if (mode.equals("nation")) {
            if (args.length < 3) {
                sender.sendMessage("§eИспользование: /vrgs sync nation <slug>");
                return;
            }
            String slug = args[2];
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getNationSyncService().syncNation(slug));
            sender.sendMessage("§aЗапущен sync государства: §f" + slug);
            return;
        }

        sender.sendMessage("§cНеизвестный режим sync.");
    }

    private void handleReward(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eИспользование: /vrgs reward <resolve|apply> <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден онлайн: " + args[2]);
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("resolve")) {
            plugin.getReferralRewardService().resolveAndMaybeApply(target, false);
            sender.sendMessage("§aЗапущен resolve reward для §f" + target.getName());
            return;
        }

        if (mode.equals("apply")) {
            plugin.getReferralRewardService().resolveAndMaybeApply(target, true);
            sender.sendMessage("§aЗапущен force apply reward для §f" + target.getName());
            return;
        }

        sender.sendMessage("§cНеизвестный режим reward.");
    }

    private void handleNation(CommandSender sender, String[] args) {
        if (args.length < 5 || !args[1].equalsIgnoreCase("set")) {
            sender.sendMessage("§eИспользование: /vrgs nation set <slug> <territory|bosskills|events|prestige> <value>");
            return;
        }

        String slug = args[2].toLowerCase(Locale.ROOT);
        String key = args[3].toLowerCase(Locale.ROOT);

        if (!Arrays.asList("territory", "bosskills", "events", "prestige").contains(key)) {
            sender.sendMessage("§cДопустимые ключи: territory, bosskills, events, prestige");
            return;
        }

        int value;
        try {
            value = Integer.parseInt(args[4]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cValue должен быть числом.");
            return;
        }

        plugin.getDataStore().setNationOverride(slug, key, value);
        plugin.getDataStore().saveNow();
        sender.sendMessage("§aOverride сохранён: §f" + slug + " / " + key + " = " + value);
    }

    private void handleMeta(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eИспользование: /vrgs meta <apply|reconcile> [player]");
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);

        if (mode.equals("reconcile")) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLuckPermsNationMetaService().reconcileOnlinePlayers());
            sender.sendMessage("§aЗапущен reconcile LuckPerms meta для онлайн игроков.");
            return;
        }

        if (mode.equals("apply")) {
            if (args.length < 3) {
                sender.sendMessage("§eИспользование: /vrgs meta apply <player>");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cИгрок не найден онлайн: " + args[2]);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLuckPermsNationMetaService().applyForPlayer(target));
            sender.sendMessage("§aЗапущено обновление nation meta для §f" + target.getName());
            return;
        }

        sender.sendMessage("§cНеизвестный режим meta.");
    }

    private void handleNations(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("refresh")) {
            sender.sendMessage("§eИспользование: /vrgs nations refresh");
            return;
        }

        plugin.getNationRegistry().refresh();
        sender.sendMessage("§aNation registry refreshed. Source: §f" + plugin.getNationRegistry().getSource());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6VoidRpGameSync commands:");
        sender.sendMessage("§e/vrgs reload");
        sender.sendMessage("§e/vrgs sync all");
        sender.sendMessage("§e/vrgs sync nation <slug>");
        sender.sendMessage("§e/vrgs nations refresh");
        sender.sendMessage("§e/vrgs meta reconcile");
        sender.sendMessage("§e/vrgs meta apply <player>");
        sender.sendMessage("§e/vrgs reward resolve <player>");
        sender.sendMessage("§e/vrgs reward apply <player>");
        sender.sendMessage("§e/vrgs nation set <slug> <territory|bosskills|events|prestige> <value>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voidrp.gamesync.admin")) return List.of();

        if (args.length == 1) {
            return filter(args[0], List.of("reload", "sync", "reward", "nation", "meta", "nations"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sync")) {
            return filter(args[1], List.of("all", "nation"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("sync") && args[1].equalsIgnoreCase("nation")) {
            List<String> slugs = new ArrayList<>();
            for (NationDefinition def : plugin.getNationRegistry().all()) slugs.add(def.slug());
            return filter(args[2], slugs);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reward")) {
            return filter(args[1], List.of("resolve", "apply"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("reward")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(args[2], names);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("meta")) {
            return filter(args[1], List.of("apply", "reconcile"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("meta") && args[1].equalsIgnoreCase("apply")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(args[2], names);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("nations")) {
            return filter(args[1], List.of("refresh"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("nation")) {
            return filter(args[1], List.of("set"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("nation") && args[1].equalsIgnoreCase("set")) {
            List<String> slugs = new ArrayList<>();
            for (NationDefinition def : plugin.getNationRegistry().all()) slugs.add(def.slug());
            return filter(args[2], slugs);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("nation") && args[1].equalsIgnoreCase("set")) {
            return filter(args[3], List.of("territory", "bosskills", "events", "prestige"));
        }

        return List.of();
    }

    private List<String> filter(String input, List<String> values) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
            .toList();
    }
}
