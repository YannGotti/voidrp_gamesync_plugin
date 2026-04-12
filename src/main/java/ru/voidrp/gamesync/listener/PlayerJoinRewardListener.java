package ru.voidrp.gamesync.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import ru.voidrp.gamesync.VoidRpGameSyncPlugin;

public final class PlayerJoinRewardListener implements Listener {

    private final VoidRpGameSyncPlugin plugin;

    public PlayerJoinRewardListener(VoidRpGameSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getGameSyncConfig().isResolveOnJoin()) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(
            plugin,
            () -> plugin.getReferralRewardService().resolveAndMaybeApply(event.getPlayer(), false),
            plugin.getGameSyncConfig().getJoinDelayTicks()
        );
    }
}
