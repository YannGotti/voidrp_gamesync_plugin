package ru.voidrp.gamesync.service;

import java.io.IOException;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;
import ru.voidrp.gamesync.config.GameSyncConfig;
import ru.voidrp.gamesync.config.NationRegistry;
import ru.voidrp.gamesync.model.NationDefinition;
import ru.voidrp.gamesync.model.NationStatsPayload;
import ru.voidrp.gamesync.store.PluginDataStore;

public final class NationSyncService {

    private final JavaPlugin plugin;
    private final BackendClient backendClient;
    private final NationRegistry registry;
    private final PluginDataStore dataStore;
    private final Economy economy;
    private final GameSyncConfig config;

    public NationSyncService(
            JavaPlugin plugin,
            BackendClient backendClient,
            NationRegistry registry,
            PluginDataStore dataStore,
            Economy economy,
            GameSyncConfig config
    ) {
        this.plugin = plugin;
        this.backendClient = backendClient;
        this.registry = registry;
        this.dataStore = dataStore;
        this.economy = economy;
        this.config = config;
    }

    public void syncAll() {
        for (NationDefinition nation : registry.all()) {
            syncNation(nation.slug());
        }
    }

    public void syncNation(String slug) {
        NationDefinition definition = registry.get(slug);
        if (definition == null) {
            plugin.getLogger().warning("Nation not found in nations.yml: " + slug);
            return;
        }

        try {
            if (config.isSyncMembership()) {
                backendClient.syncNationMembership(definition);
                if (config.isVerboseSync()) {
                    plugin.getLogger().info("Membership synced for nation " + definition.slug());
                }
            }

            if (config.isSyncStats()) {
                NationStatsPayload payload = buildStatsPayload(definition);
                backendClient.upsertNationStats(payload);
                if (config.isVerboseSync()) {
                    plugin.getLogger().info("Stats synced for nation " + definition.slug());
                }
            }
        } catch (IOException | InterruptedException exception) {
            plugin.getLogger().warning("Failed to sync nation " + definition.slug() + ": " + exception.getMessage());
        } catch (Exception exception) {
            plugin.getLogger().warning("Unexpected sync error for nation " + definition.slug() + ": " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    public NationStatsPayload buildStatsPayload(NationDefinition definition) {
        double treasury = 0D;
        int playtimeMinutes = 0;
        int pvpKills = 0;
        int mobKills = 0;
        int deaths = 0;

        long blocksPlaced = 0L;
        long blocksBroken = 0L;

        List<String> allMembers = definition.allMembersIncludingRoles();

        for (String nickname : allMembers) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(nickname);

            if (economy != null) {
                try {
                    treasury += economy.getBalance(offlinePlayer);
                } catch (Throwable ignored) {
                    // ignore economy provider edge cases
                }
            }

            Player onlinePlayer = Bukkit.getPlayerExact(nickname);
            if (onlinePlayer == null) {
                continue;
            }

            try {
                playtimeMinutes += onlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60);
            } catch (Exception ignored) {
            }

            try {
                pvpKills += onlinePlayer.getStatistic(Statistic.PLAYER_KILLS);
            } catch (Exception ignored) {
            }

            try {
                mobKills += onlinePlayer.getStatistic(Statistic.MOB_KILLS);
            } catch (Exception ignored) {
            }

            try {
                deaths += onlinePlayer.getStatistic(Statistic.DEATHS);
            } catch (Exception ignored) {
            }

            // USE_ITEM и MINE_BLOCK требуют дополнительный параметр Material.
            // Пока оставляем 0, чтобы sync не падал.
        }

        int territoryPoints = definition.territoryPoints() + dataStore.getNationOverride(definition.slug(), "territory");
        int bossKills = definition.bossKills() + dataStore.getNationOverride(definition.slug(), "bosskills");
        int eventsCompleted = definition.eventsCompleted() + dataStore.getNationOverride(definition.slug(), "events");
        int prestigeBonus = definition.prestigeBonus() + dataStore.getNationOverride(definition.slug(), "prestige");

        int prestigeScore = (int) Math.round(
                treasury * 0.002
                + territoryPoints * 15
                + playtimeMinutes * 0.05
                + pvpKills * 8
                + mobKills * 0.2
                + bossKills * 25
                + eventsCompleted * 20
                + prestigeBonus
        );

        return new NationStatsPayload(
                definition.slug(),
                round2(treasury),
                territoryPoints,
                playtimeMinutes,
                pvpKills,
                mobKills,
                bossKills,
                deaths,
                blocksPlaced,
                blocksBroken,
                eventsCompleted,
                prestigeScore
        );
    }

    private double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
