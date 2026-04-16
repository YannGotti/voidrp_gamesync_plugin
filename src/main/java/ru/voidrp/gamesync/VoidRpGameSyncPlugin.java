package ru.voidrp.gamesync;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;
import ru.voidrp.gamesync.command.PlayerNationDonateCommand;
import ru.voidrp.gamesync.command.PlayerNationTreasuryCommand;
import ru.voidrp.gamesync.command.PlayerNationTreasuryHistoryCommand;
import ru.voidrp.gamesync.command.PlayerNationWithdrawCommand;
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
    private BackendClient backendClient;
    private NationRegistry nationRegistry;
    private PluginDataStore dataStore;
    private RewardCacheService rewardCacheService;
    private ReferralRewardService referralRewardService;
    private LuckPermsNationMetaService luckPermsNationMetaService;
    private TerritoryPointsResolver territoryPointsResolver;
    private NationSyncService nationSyncService;
    private SyncScheduler syncScheduler;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("nations.yml", false);
        saveResource("data.yml", false);

        setupEconomy();

        this.gameSyncConfig = new GameSyncConfig(this);
        this.dataStore = new PluginDataStore(this);
        this.backendClient = new BackendClient(this, gameSyncConfig);
        this.rewardCacheService = new RewardCacheService(this, dataStore);
        this.nationRegistry = new NationRegistry(this, backendClient, gameSyncConfig);
        this.territoryPointsResolver = new TerritoryPointsResolver(this, dataStore, gameSyncConfig);
        this.referralRewardService = new ReferralRewardService(this, backendClient, rewardCacheService, gameSyncConfig);
        this.luckPermsNationMetaService = new LuckPermsNationMetaService(
                this,
                nationRegistry,
                dataStore,
                gameSyncConfig
        );
        this.nationSyncService = new NationSyncService(
                this,
                backendClient,
                nationRegistry,
                dataStore,
                economy,
                gameSyncConfig,
                territoryPointsResolver
        );
        this.syncScheduler = new SyncScheduler(
                this,
                nationSyncService,
                luckPermsNationMetaService,
                gameSyncConfig
        );

        registerCommands();
        registerListeners();

        if (gameSyncConfig.isSyncEnabled()) {
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
        this.backendClient = new BackendClient(this, gameSyncConfig);
        this.nationRegistry = new NationRegistry(this, backendClient, gameSyncConfig);
        this.territoryPointsResolver = new TerritoryPointsResolver(this, dataStore, gameSyncConfig);
        this.rewardCacheService = new RewardCacheService(this, dataStore);
        this.referralRewardService = new ReferralRewardService(this, backendClient, rewardCacheService, gameSyncConfig);
        this.luckPermsNationMetaService = new LuckPermsNationMetaService(
                this,
                nationRegistry,
                dataStore,
                gameSyncConfig
        );
        this.nationSyncService = new NationSyncService(
                this,
                backendClient,
                nationRegistry,
                dataStore,
                economy,
                gameSyncConfig,
                territoryPointsResolver
        );

        if (this.syncScheduler != null) {
            this.syncScheduler.stop();
        }

        this.syncScheduler = new SyncScheduler(
                this,
                nationSyncService,
                luckPermsNationMetaService,
                gameSyncConfig
        );

        if (gameSyncConfig.isSyncEnabled()) {
            this.syncScheduler.start();
        }
    }

    private void registerCommands() {
        VoidRpAdminCommand adminCommand = new VoidRpAdminCommand(this);
        registerCommand("vrgs", adminCommand, adminCommand);

        PlayerNationDonateCommand donateCommand = new PlayerNationDonateCommand(this);
        registerCommand("nationdonate", donateCommand, donateCommand);
        registerCommand("ndonate", donateCommand, donateCommand);

        PlayerNationTreasuryCommand treasuryCommand = new PlayerNationTreasuryCommand(this);
        registerCommand("nationtreasury", treasuryCommand, treasuryCommand);
        registerCommand("ntreasury", treasuryCommand, treasuryCommand);

        PlayerNationTreasuryHistoryCommand treasuryHistoryCommand = new PlayerNationTreasuryHistoryCommand(this);
        registerCommand("nationtreasuryhistory", treasuryHistoryCommand, treasuryHistoryCommand);
        registerCommand("ntreasuryhistory", treasuryHistoryCommand, treasuryHistoryCommand);

        PlayerNationWithdrawCommand withdrawCommand = new PlayerNationWithdrawCommand(this);
        registerCommand("nationwithdraw", withdrawCommand, withdrawCommand);
        registerCommand("nwithdraw", withdrawCommand, withdrawCommand);
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(completer);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinRewardListener(this), this);
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found. Economy-dependent features may be limited.");
            this.economy = null;
            return;
        }

        RegisteredServiceProvider<Economy> provider
                = getServer().getServicesManager().getRegistration(Economy.class);

        if (provider == null) {
            getLogger().warning("No Economy provider found via Vault.");
            this.economy = null;
            return;
        }

        this.economy = provider.getProvider();
        if (this.economy == null) {
            getLogger().warning("Economy provider resolved as null.");
        }
    }

    public GameSyncConfig getGameSyncConfig() {
        return gameSyncConfig;
    }

    public BackendClient getBackendClient() {
        return backendClient;
    }

    public NationRegistry getNationRegistry() {
        return nationRegistry;
    }

    public PluginDataStore getDataStore() {
        return dataStore;
    }

    public RewardCacheService getRewardCacheService() {
        return rewardCacheService;
    }

    public ReferralRewardService getReferralRewardService() {
        return referralRewardService;
    }

    public LuckPermsNationMetaService getLuckPermsNationMetaService() {
        return luckPermsNationMetaService;
    }

    public TerritoryPointsResolver getTerritoryPointsResolver() {
        return territoryPointsResolver;
    }

    public NationSyncService getNationSyncService() {
        return nationSyncService;
    }

    public Economy getEconomy() {
        return economy;
    }
}