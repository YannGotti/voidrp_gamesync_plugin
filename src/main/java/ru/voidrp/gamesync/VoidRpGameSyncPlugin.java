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
import ru.voidrp.gamesync.service.SkinCommandService;
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
    private SkinCommandService skinCommandService;
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
        this.skinCommandService = new SkinCommandService(this);

        registerCommands();
        registerListeners();

        if (gameSyncConfig.isSyncEnabled()) {
            syncScheduler.start();
        }

        if (gameSyncConfig.isSkinSyncEnabled() && !skinCommandService.isAvailable()) {
            getLogger().warning("SkinsRestorer not found. Skin synchronization is enabled in config, but applying skins will be skipped.");
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
        this.skinCommandService = new SkinCommandService(this);

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
        bindCommand("vrgs", new VoidRpAdminCommand(this), new VoidRpAdminCommand(this));
        bindCommand("nationdonate", new PlayerNationDonateCommand(this), new PlayerNationDonateCommand(this));
        bindCommand("nationtreasury", new PlayerNationTreasuryCommand(this), new PlayerNationTreasuryCommand(this));
        bindCommand("nationtreasuryhistory", new PlayerNationTreasuryHistoryCommand(this), new PlayerNationTreasuryHistoryCommand(this));
        bindCommand("nationwithdraw", new PlayerNationWithdrawCommand(this), new PlayerNationWithdrawCommand(this));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinRewardListener(this), this);
    }

    private void bindCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command is missing in plugin.yml: " + name);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            getLogger().warning("Vault economy provider not found. Treasury operations will be limited.");
            economy = null;
            return;
        }
        economy = provider.getProvider();
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

    public SyncScheduler getSyncScheduler() {
        return syncScheduler;
    }

    public SkinCommandService getSkinCommandService() {
        return skinCommandService;
    }

    public Economy getEconomy() {
        return economy;
    }
}
