package ru.voidrp.gamesync.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class GameSyncConfig {

    private final String backendBaseUrl;
    private final String apiPrefix;
    private final String gameAuthSecret;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final boolean backendNationSourceEnabled;
    private final boolean fallbackYmlEnabled;
    private final boolean syncEnabled;
    private final long syncPeriodTicks;
    private final boolean syncMembership;
    private final boolean syncStats;
    private final boolean statsOnlineOnly;
    private final String territorySourceMode;
    private final String territoryWorldGuardCountMode;
    private final boolean territoryWorldGuardFallbackToManual;
    private final boolean resolveOnJoin;
    private final long joinDelayTicks;
    private final boolean autoApplyOnJoin;
    private final boolean suppressDuplicateRewardUntilExpiry;
    private final boolean luckPermsEnabled;
    private final boolean applyNationMetaOnJoin;
    private final boolean reconcileNationMetaAfterSync;
    private final String luckPermsServerContext;
    private final boolean prefixEnabled;
    private final int prefixPriority;
    private final String prefixTemplate;
    private final boolean suffixEnabled;
    private final int suffixPriority;
    private final String suffixTemplate;
    private final String leaderLabel;
    private final String officerLabel;
    private final String memberLabel;
    private final boolean debugHttp;
    private final boolean verboseSync;
    private final ConfigurationSection rewardBundlesSection;
    private final JavaPlugin plugin;

    public GameSyncConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.backendBaseUrl = trimTrailingSlash(plugin.getConfig().getString("backend.base-url", "https://api.void-rp.ru"));
        this.apiPrefix = plugin.getConfig().getString("backend.api-prefix", "/api/v1");
        this.gameAuthSecret = plugin.getConfig().getString("backend.game-auth-secret", "");
        this.connectTimeoutMs = plugin.getConfig().getInt("backend.connect-timeout-ms", 10000);
        this.readTimeoutMs = plugin.getConfig().getInt("backend.read-timeout-ms", 20000);

        this.backendNationSourceEnabled = plugin.getConfig().getBoolean("nation-source.backend-enabled", true);
        this.fallbackYmlEnabled = plugin.getConfig().getBoolean("nation-source.fallback-yml-enabled", true);

        this.syncEnabled = plugin.getConfig().getBoolean("sync.enabled", true);
        this.syncPeriodTicks = Math.max(20L, plugin.getConfig().getLong("sync.period-seconds", 300L) * 20L);
        this.syncMembership = plugin.getConfig().getBoolean("sync.sync-membership", true);
        this.syncStats = plugin.getConfig().getBoolean("sync.sync-stats", true);
        this.statsOnlineOnly = plugin.getConfig().getBoolean("sync.stats-online-only", false);

        this.territorySourceMode = plugin.getConfig().getString("territory.source", "manual");
        this.territoryWorldGuardCountMode = plugin.getConfig().getString("territory.worldguard.count-mode", "2d");
        this.territoryWorldGuardFallbackToManual = plugin.getConfig().getBoolean("territory.worldguard.fallback_to_manual", true);

        this.resolveOnJoin = plugin.getConfig().getBoolean("referrals.resolve-on-join", true);
        this.joinDelayTicks = Math.max(1L, plugin.getConfig().getLong("referrals.join-delay-ticks", 60L));
        this.autoApplyOnJoin = plugin.getConfig().getBoolean("referrals.auto-apply-on-join", true);
        this.suppressDuplicateRewardUntilExpiry = plugin.getConfig().getBoolean("referrals.suppress-duplicate-reward-until-expiry", true);

        this.luckPermsEnabled = plugin.getConfig().getBoolean("luckperms.enabled", true);
        this.applyNationMetaOnJoin = plugin.getConfig().getBoolean("luckperms.apply-on-join", true);
        this.reconcileNationMetaAfterSync = plugin.getConfig().getBoolean("luckperms.reconcile-after-sync", true);
        this.luckPermsServerContext = plugin.getConfig().getString("luckperms.server-context", "");
        this.prefixEnabled = plugin.getConfig().getBoolean("luckperms.prefix.enabled", true);
        this.prefixPriority = plugin.getConfig().getInt("luckperms.prefix.priority", 25);
        this.prefixTemplate = plugin.getConfig().getString("luckperms.prefix.template", "&8[&b%tag%&8]&r ");
        this.suffixEnabled = plugin.getConfig().getBoolean("luckperms.suffix.enabled", true);
        this.suffixPriority = plugin.getConfig().getInt("luckperms.suffix.priority", 25);
        this.suffixTemplate = plugin.getConfig().getString("luckperms.suffix.template", " &8• &7%role%");
        this.leaderLabel = plugin.getConfig().getString("luckperms.role-labels.leader", "Лидер");
        this.officerLabel = plugin.getConfig().getString("luckperms.role-labels.officer", "Офицер");
        this.memberLabel = plugin.getConfig().getString("luckperms.role-labels.member", "Участник");

        this.debugHttp = plugin.getConfig().getBoolean("debug.log-http", false);
        this.verboseSync = plugin.getConfig().getBoolean("debug.verbose-sync", false);

        this.rewardBundlesSection = plugin.getConfig().getConfigurationSection("reward-bundles");
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public String getApiPrefix() {
        return apiPrefix;
    }

    public String getGameAuthSecret() {
        return gameAuthSecret;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public boolean isBackendNationSourceEnabled() {
        return backendNationSourceEnabled;
    }

    public boolean isFallbackYmlEnabled() {
        return fallbackYmlEnabled;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public long getSyncPeriodTicks() {
        return syncPeriodTicks;
    }

    public boolean isSyncMembership() {
        return syncMembership;
    }

    public boolean isSyncStats() {
        return syncStats;
    }

    public boolean isStatsOnlineOnly() {
        return statsOnlineOnly;
    }

    public String getTerritorySourceMode() {
        return territorySourceMode;
    }

    public String getTerritoryWorldGuardCountMode() {
        return territoryWorldGuardCountMode;
    }

    public boolean isTerritoryWorldGuardFallbackToManual() {
        return territoryWorldGuardFallbackToManual;
    }

    public boolean isResolveOnJoin() {
        return resolveOnJoin;
    }

    public long getJoinDelayTicks() {
        return joinDelayTicks;
    }

    public boolean isAutoApplyOnJoin() {
        return autoApplyOnJoin;
    }

    public boolean isSuppressDuplicateRewardUntilExpiry() {
        return suppressDuplicateRewardUntilExpiry;
    }

    public boolean isLuckPermsEnabled() {
        return luckPermsEnabled;
    }

    public boolean isApplyNationMetaOnJoin() {
        return applyNationMetaOnJoin;
    }

    public boolean isReconcileNationMetaAfterSync() {
        return reconcileNationMetaAfterSync;
    }

    public String getLuckPermsServerContext() {
        return luckPermsServerContext;
    }

    public boolean isPrefixEnabled() {
        return prefixEnabled;
    }

    public int getPrefixPriority() {
        return prefixPriority;
    }

    public String getPrefixTemplate() {
        return prefixTemplate;
    }

    public boolean isSuffixEnabled() {
        return suffixEnabled;
    }

    public int getSuffixPriority() {
        return suffixPriority;
    }

    public String getSuffixTemplate() {
        return suffixTemplate;
    }

    public String getLeaderLabel() {
        return leaderLabel;
    }

    public String getOfficerLabel() {
        return officerLabel;
    }

    public String getMemberLabel() {
        return memberLabel;
    }

    public boolean isDebugHttp() {
        return debugHttp;
    }

    public boolean isVerboseSync() {
        return verboseSync;
    }

    public ConfigurationSection getRewardBundlesSection() {
        return rewardBundlesSection;
    }

    public java.util.List<String> getRewardCommands(String bundleKey) {
        if (bundleKey == null || bundleKey.isBlank()) {
            return java.util.List.of();
        }

        String path = "reward-bundles." + bundleKey + ".commands";
        java.util.List<String> commands = plugin.getConfig().getStringList(path);
        if (commands == null || commands.isEmpty()) {
            return java.util.List.of();
        }
        return java.util.List.copyOf(commands);
    }

    public String roleDisplay(String role) {
        if (role == null || role.isBlank()) {
            return "Участник";
        }

        String normalized = role.trim().toLowerCase(java.util.Locale.ROOT);
        String configured = plugin.getConfig().getString("luckperms.role-labels." + normalized);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        return switch (normalized) {
            case "leader" ->
                "Лидер";
            case "officer" ->
                "Офицер";
            case "member" ->
                "Участник";
            default ->
                role;
        };
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
