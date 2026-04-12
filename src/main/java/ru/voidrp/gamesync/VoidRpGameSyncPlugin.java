package ru.voidrp.gamesync;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;
import ru.voidrp.gamesync.command.VoidRpAdminCommand;
import ru.voidrp.gamesync.config.GameSyncConfig;
import ru.voidrp.gamesync.config.NationRegistry;
import ru.voidrp.gamesync.listener.PlayerJoinRewardListener;
import ru.voidrp.gamesync.service.BackendClient;
import ru.voidrp.gamesync.service.LuckPermsNationMetaService;
import ru.voidrp.gamesync.service.NationSyncService;
import ru.voidrp.gamesync.service.ReferralRewardService;
import ru.voidrp.gamesync.service.RewardCacheService;
import ru.voidrp.gamesync.service.SyncScheduler;
import ru.voidrp.gamesync.service.TerritoryPointsResolver;
import ru.voidrp.gamesync.store.PluginDataStore;

public final class VoidRpGameSyncPlugin extends JavaPlugin {

    private GameSyncConfig gameSyncConfig;
    private NationRegistry nationRegistry;
    private PluginDataStore dataStore;
    private BackendClient backendClient;
    private TerritoryPointsResolver territoryPointsResolver;
    private NationSyncService nationSyncService;
    private RewardCacheService rewardCacheService;
    private ReferralRewardService referralRewardService;
    private LuckPermsNationMetaService luckPermsNationMetaService;
    private SyncScheduler syncScheduler;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("nations.yml");
        saveResourceIfMissing("data.yml");
        this.economy = setupEconomy();
        reloadPluginState();

        PluginCommand command = getCommand("vrgs");
        if (command != null) {
            VoidRpAdminCommand executor = new VoidRpAdminCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new PlayerJoinRewardListener(this), this);

        if (syncScheduler != null) {
            syncScheduler.start();
        }

        getLogger().info("VoidRpGameSync enabled.");
    }

    @Override
    public void onDisable() {
        if (syncScheduler != null) {
            syncScheduler.stop();
        }
        if (dataStore != null) {
            dataStore.saveNow();
        }
        getLogger().info("VoidRpGameSync disabled.");
    }

    public void reloadPluginState() {
        reloadConfig();

        this.gameSyncConfig = new GameSyncConfig(this);
        this.dataStore = new PluginDataStore(this);
        this.backendClient = new BackendClient(this, gameSyncConfig);
        this.nationRegistry = new NationRegistry(this, backendClient, gameSyncConfig);
        this.territoryPointsResolver = new TerritoryPointsResolver(this, dataStore, gameSyncConfig);
        this.rewardCacheService = new RewardCacheService(this, dataStore);
        this.referralRewardService = new ReferralRewardService(this, backendClient, rewardCacheService, gameSyncConfig);
        this.nationSyncService = new NationSyncService(this, backendClient, nationRegistry, dataStore, economy, gameSyncConfig, territoryPointsResolver);
        this.luckPermsNationMetaService = new LuckPermsNationMetaService(this, nationRegistry, dataStore, gameSyncConfig);
        this.syncScheduler = new SyncScheduler(this, nationSyncService, luckPermsNationMetaService, gameSyncConfig);
    }

    private void saveResourceIfMissing(String path) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        java.io.File target = new java.io.File(getDataFolder(), path);
        if (!target.exists()) {
            saveResource(path, false);
        }
    }

    private Economy setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault not found. Economy sync will work with 0 balances.");
            return null;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Vault found, but no Economy provider registered.");
            return null;
        }

        getLogger().info("Economy provider detected: " + rsp.getProvider().getName());
        return rsp.getProvider();
    }

    public GameSyncConfig getGameSyncConfig() { return gameSyncConfig; }
    public NationRegistry getNationRegistry() { return nationRegistry; }
    public PluginDataStore getDataStore() { return dataStore; }
    public BackendClient getBackendClient() { return backendClient; }
    public TerritoryPointsResolver getTerritoryPointsResolver() { return territoryPointsResolver; }
    public NationSyncService getNationSyncService() { return nationSyncService; }
    public RewardCacheService getRewardCacheService() { return rewardCacheService; }
    public ReferralRewardService getReferralRewardService() { return referralRewardService; }
    public LuckPermsNationMetaService getLuckPermsNationMetaService() { return luckPermsNationMetaService; }
    public SyncScheduler getSyncScheduler() { return syncScheduler; }
    public Economy getEconomy() { return economy; }
}
