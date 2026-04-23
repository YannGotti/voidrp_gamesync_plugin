package ru.voidrp.gamesync.service;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import ru.voidrp.gamesync.VoidRpGameSyncPlugin;
import ru.voidrp.gamesync.model.PlayerSkinResponse;

public final class SkinCommandService {

    private final VoidRpGameSyncPlugin plugin;

    public SkinCommandService(VoidRpGameSyncPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("SkinsRestorer") != null
                && plugin.getServer().getPluginManager().getPlugin("SkinsRestorer").isEnabled();
    }

    public void applyOrClear(Player player, PlayerSkinResponse response) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (!isAvailable()) {
            if (plugin.getGameSyncConfig().isVerboseSync()) {
                plugin.getLogger().warning("SkinsRestorer not found or not enabled. Skin sync skipped for " + player.getName());
            }
            return;
        }

        if (response == null || !response.player_exists) {
            if (plugin.getGameSyncConfig().isVerboseSync()) {
                plugin.getLogger().warning("Skin sync skipped for " + player.getName() + ": backend player record not found.");
            }
            return;
        }

        if (!response.has_skin || response.skin_url == null || response.skin_url.isBlank()) {
            if (plugin.getGameSyncConfig().isSkinClearWhenMissing()) {
                dispatch("skin clear " + player.getName());
            }
            return;
        }

        String variant = normalizeVariant(response.model_variant);
        String customSkinName = buildCustomSkinName(response.sha256);
        dispatch("sr createcustom " + customSkinName + " " + response.skin_url + " " + variant);
        dispatch("skin set " + customSkinName + " " + player.getName());
    }

    private void dispatch(String command) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        Bukkit.dispatchCommand(console, command);
        if (plugin.getGameSyncConfig().isVerboseSync()) {
            plugin.getLogger().info("[SkinSync] Executed: " + command);
        }
    }

    private String buildCustomSkinName(String sha256) {
        String normalized = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.isBlank()) {
            return "voidrp_default_skin";
        }
        int max = Math.min(16, normalized.length());
        return "voidrp_" + normalized.substring(0, max);
    }

    private String normalizeVariant(String raw) {
        return "slim".equalsIgnoreCase(raw) ? "slim" : "classic";
    }
}
