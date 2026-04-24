package ru.voidrp.gamesync.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import ru.voidrp.gamesync.VoidRpGameSyncPlugin;
import ru.voidrp.gamesync.model.PlayerSkinResponse;
import ru.voidrp.gamesync.service.TerritoryPointsResolver.TerritoryDebugReport;
import ru.voidrp.gamesync.service.TerritoryPointsResolver.TerritoryMatch;

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
            case "skin" -> {
                handleSkin(sender, args);
                return true;
            }
            case "territory" -> {
                handleTerritory(sender, args);
                return true;
            }
            case "reward" -> {
                handleReward(sender, args);
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
            sender.sendMessage("§eИспользование: /vrgs sync <all|nation|player> [slug|nick]");
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

        if (mode.equals("player")) {
            if (args.length < 3) {
                sender.sendMessage("§eИспользование: /vrgs sync player <nick>");
                return;
            }
            String nick = args[2];
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getNationSyncService().syncNationForPlayer(nick));
            sender.sendMessage("§aЗапущен sync по игроку: §f" + nick);
            return;
        }

        sender.sendMessage("§cНеизвестный режим sync.");
    }

    private void handleSkin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eИспользование: /vrgs skin <refresh|clear> <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден онлайн: " + args[2]);
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("clear")) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getSkinCommandService().clear(target));
            sender.sendMessage("§aОтправлена команда очистки скина для §f" + target.getName());
            return;
        }

        if (mode.equals("refresh")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    PlayerSkinResponse response = plugin.getBackendClient().getPlayerSkin(target.getName());
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getSkinCommandService().refresh(target, response));
                } catch (Exception exception) {
                    sender.sendMessage("§cНе удалось обновить skin: §f" + exception.getMessage());
                }
            });
            sender.sendMessage("§aЗапрошено обновление skin для §f" + target.getName());
            return;
        }

        sender.sendMessage("§cИспользование: /vrgs skin <refresh|clear> <player>");
    }

    private void handleTerritory(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("debug")) {
            sender.sendMessage("§eИспользование: /vrgs territory debug <slug>");
            return;
        }

        String slug = args[2];
        try {
            TerritoryDebugReport report = plugin.getNationSyncService().buildTerritoryDebugReport(slug);
            sender.sendMessage("§6=== Territory debug: §f" + slug + " §6===");
            sender.sendMessage("§7Source: §f" + report.source + " §8| §7Resolution: §f" + report.resolutionMode);
            sender.sendMessage("§7Manual: §f" + report.manualValue + " §8| §7WorldGuard: §f" + report.worldguardValue + " §8| §7Final: §f" + report.finalValue);
            sender.sendMessage("§7Members checked: §f" + report.membersChecked + " §8| §7UUID resolved: §f" + report.memberUuidsResolved + " §8| §7Name resolved: §f" + report.memberNamesResolved);
            sender.sendMessage("§7Regions scanned: §f" + report.regionsScanned + " §8| §7Regions matched: §f" + report.matches.size());

            if (report.error != null && !report.error.isBlank()) {
                sender.sendMessage("§cError: " + report.error);
            }

            if (!report.unresolvedMembers.isEmpty()) {
                sender.sendMessage("§eНе удалось распознать UUID для:");
                int limit = Math.min(10, report.unresolvedMembers.size());
                for (int i = 0; i < limit; i++) {
                    sender.sendMessage("§8- §f" + report.unresolvedMembers.get(i));
                }
                if (report.unresolvedMembers.size() > limit) {
                    sender.sendMessage("§8... ещё " + (report.unresolvedMembers.size() - limit));
                }
            }

            if (report.matches.isEmpty()) {
                sender.sendMessage("§eСовпадений регионов не найдено.");
                sender.sendMessage("§7Проверь, что участники государства являются owners/members в WorldGuard регионах.");
            } else {
                int limit = Math.min(15, report.matches.size());
                for (int i = 0; i < limit; i++) {
                    TerritoryMatch match = report.matches.get(i);
                    sender.sendMessage("§8- §f" + match.worldName() + ":" + match.regionId()
                            + " §7type=§f" + match.matchType()
                            + " §7value=§f" + match.matchedValue()
                            + " §7area=§f" + match.contributedArea()
                            + " §7mode=§f" + match.countMode());
                }
                if (report.matches.size() > limit) {
                    sender.sendMessage("§8... ещё " + (report.matches.size() - limit) + " регионов");
                }
            }
        } catch (Exception exception) {
            sender.sendMessage("§cНе удалось собрать territory debug: §f" + exception.getMessage());
        }
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6/vrgs reload");
        sender.sendMessage("§6/vrgs sync all");
        sender.sendMessage("§6/vrgs sync nation <slug>");
        sender.sendMessage("§6/vrgs sync player <nick>");
        sender.sendMessage("§6/vrgs skin refresh <player>");
        sender.sendMessage("§6/vrgs skin clear <player>");
        sender.sendMessage("§6/vrgs territory debug <slug>");
        sender.sendMessage("§6/vrgs reward <resolve|apply> <player>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voidrp.gamesync.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("reload", "sync", "skin", "territory", "reward"), args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "sync" -> filter(List.of("all", "nation", "player"), args[1]);
                case "skin" -> filter(List.of("refresh", "clear"), args[1]);
                case "territory" -> filter(List.of("debug"), args[1]);
                case "reward" -> filter(List.of("resolve", "apply"), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("sync") && args[1].equalsIgnoreCase("player")) {
                return filter(onlinePlayers(), args[2]);
            }
            if (sub.equals("skin")) {
                return filter(onlinePlayers(), args[2]);
            }
            if (sub.equals("reward")) {
                return filter(onlinePlayers(), args[2]);
            }
            if (sub.equals("territory")) {
                return List.of();
            }
        }

        return List.of();
    }

    private List<String> onlinePlayers() {
        List<String> values = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            values.add(player.getName());
        }
        return values;
    }

    private List<String> filter(List<String> source, String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(item -> item.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .toList();
    }
}
