package ru.voidrp.gamesync.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class GameSyncConfig {

    private final String backendBaseUrl;
    private final String apiPrefix;
    private final String gameAuthSecret;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private final boolean syncEnabled;
    private final long syncPeriodTicks;
    private final boolean syncMembership;
    private final boolean syncStats;
    private final boolean statsOnlineOnly;

    private final boolean resolveOnJoin;
    private final long joinDelayTicks;
    private final boolean autoApplyOnJoin;
    private final boolean suppressDuplicateRewardUntilExpiry;

    private final boolean debugHttp;
    private final boolean verboseSync;

    private final ConfigurationSection rewardBundlesSection;

    public GameSyncConfig(JavaPlugin plugin) {
        this.backendBaseUrl = trimTrailingSlash(plugin.getConfig().getString("backend.base-url", "https://api.void-rp.ru"));
        this.apiPrefix = plugin.getConfig().getString("backend.api-prefix", "/api/v1");
        this.gameAuthSecret = plugin.getConfig().getString("backend.game-auth-secret", "");
        this.connectTimeoutMs = plugin.getConfig().getInt("backend.connect-timeout-ms", 10000);
        this.readTimeoutMs = plugin.getConfig().getInt("backend.read-timeout-ms", 20000);

        this.syncEnabled = plugin.getConfig().getBoolean("sync.enabled", true);
        this.syncPeriodTicks = Math.max(20L, plugin.getConfig().getLong("sync.period-seconds", 300L) * 20L);
        this.syncMembership = plugin.getConfig().getBoolean("sync.sync-membership", true);
        this.syncStats = plugin.getConfig().getBoolean("sync.sync-stats", true);
        this.statsOnlineOnly = plugin.getConfig().getBoolean("sync.stats-online-only", true);

        this.resolveOnJoin = plugin.getConfig().getBoolean("referrals.resolve-on-join", true);
        this.joinDelayTicks = Math.max(1L, plugin.getConfig().getLong("referrals.join-delay-ticks", 60L));
        this.autoApplyOnJoin = plugin.getConfig().getBoolean("referrals.auto-apply-on-join", true);
        this.suppressDuplicateRewardUntilExpiry = plugin.getConfig().getBoolean("referrals.suppress-duplicate-reward-until-expiry", true);

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

    public boolean isDebugHttp() {
        return debugHttp;
    }

    public boolean isVerboseSync() {
        return verboseSync;
    }

    public List<String> getRewardCommands(String bundleKey) {
        if (rewardBundlesSection == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = rewardBundlesSection.getConfigurationSection(bundleKey);
        if (section == null) {
            return Collections.emptyList();
        }

        return section.getStringList("commands");
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
