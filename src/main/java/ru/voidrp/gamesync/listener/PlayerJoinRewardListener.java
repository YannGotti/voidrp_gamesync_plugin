package ru.voidrp.gamesync.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import ru.voidrp.gamesync.VoidRpGameSyncPlugin;
import ru.voidrp.gamesync.model.PlayerSkinResponse;

public final class PlayerJoinRewardListener implements Listener {

    private final VoidRpGameSyncPlugin plugin;

    public PlayerJoinRewardListener(VoidRpGameSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getGameSyncConfig().isResolveOnJoin()) {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                plugin,
                () -> plugin.getReferralRewardService().resolveAndMaybeApply(event.getPlayer(), false),
                plugin.getGameSyncConfig().getJoinDelayTicks()
            );
        }

        if (plugin.getGameSyncConfig().isApplyNationMetaOnJoin()) {
            plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> plugin.getLuckPermsNationMetaService().applyForPlayer(event.getPlayer()),
                Math.max(20L, plugin.getGameSyncConfig().getJoinDelayTicks())
            );
        }

        if (plugin.getGameSyncConfig().isSkinSyncEnabled() && plugin.getGameSyncConfig().isSkinApplyOnJoin()) {
            scheduleSkinApply(event.getPlayer());
        }
    }

    private void scheduleSkinApply(Player player) {
        long delayTicks = plugin.getGameSyncConfig().getSkinJoinDelayTicks();

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                plugin,
                () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    try {
                        PlayerSkinResponse response = plugin.getBackendClient().getPlayerSkin(player.getName());
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> plugin.getSkinCommandService().applyOrClear(player, response)
                        );
                    } catch (Exception exception) {
                        if (plugin.getGameSyncConfig().isVerboseSync()) {
                            plugin.getLogger().warning("Failed to sync skin for " + player.getName() + ": " + exception.getMessage());
                        }
                    }
                },
                delayTicks
        );
    }
}
