package ru.voidrp.gamesync.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.voidrp.gamesync.config.GameSyncConfig;
import ru.voidrp.gamesync.model.NationDefinition;
import ru.voidrp.gamesync.store.PluginDataStore;

public final class TerritoryPointsResolver {

    private final JavaPlugin plugin;
    private final PluginDataStore dataStore;
    private final GameSyncConfig config;

    public TerritoryPointsResolver(JavaPlugin plugin, PluginDataStore dataStore, GameSyncConfig config) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.config = config;
    }

    public int resolve(NationDefinition definition) {
        int manualValue = definition.territoryPoints() + dataStore.getNationOverride(definition.slug(), "territory");

        String source = config.getTerritorySourceMode();
        if (source == null || source.isBlank() || source.equalsIgnoreCase("manual")) {
            return manualValue;
        }

        if (source.equalsIgnoreCase("worldguard")) {
            Integer resolved = resolveViaWorldGuard(definition);
            if (resolved != null) {
                return resolved;
            }
            if (config.isTerritoryWorldGuardFallbackToManual()) {
                return manualValue;
            }
            return 0;
        }

        return manualValue;
    }

    public TerritoryDebugReport buildDebugReport(NationDefinition definition) {
        int manualValue = definition.territoryPoints() + dataStore.getNationOverride(definition.slug(), "territory");
        String source = config.getTerritorySourceMode();

        TerritoryDebugReport report = new TerritoryDebugReport(
            definition.slug(),
            source,
            manualValue,
            config.getTerritoryWorldGuardCountMode(),
            config.isTerritoryWorldGuardFallbackToManual()
        );

        if (source == null || source.isBlank() || source.equalsIgnoreCase("manual")) {
            report.finalValue = manualValue;
            report.resolutionMode = "manual";
            return report;
        }

        if (!source.equalsIgnoreCase("worldguard")) {
            report.finalValue = manualValue;
            report.resolutionMode = "unknown-source-fallback-manual";
            return report;
        }

        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            report.finalValue = config.isTerritoryWorldGuardFallbackToManual() ? manualValue : 0;
            report.resolutionMode = config.isTerritoryWorldGuardFallbackToManual()
                ? "worldguard-missing-fallback-manual"
                : "worldguard-missing-zero";
            return report;
        }

        Set<UUID> memberUuids = resolveMemberUuids(definition.allMembersIncludingRoles());
        Set<String> memberNames = resolveMemberNames(definition.allMembersIncludingRoles());
        report.memberUuidsResolved = memberUuids.size();
        report.memberNamesResolved = memberNames.size();

        if (memberUuids.isEmpty() && memberNames.isEmpty()) {
            report.finalValue = 0;
            report.resolutionMode = "no-members";
            return report;
        }

        String countMode = config.getTerritoryWorldGuardCountMode();
        boolean count3d = countMode != null && countMode.equalsIgnoreCase("3d");

        long total = 0L;
        Set<String> countedRegionKeys = new HashSet<>();

        try {
            for (World world : Bukkit.getWorlds()) {
                RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

                if (regionManager == null) {
                    continue;
                }

                for (ProtectedRegion region : regionManager.getRegions().values()) {
                    OwnershipMatch match = findOwnershipMatch(region.getOwners(), memberUuids, memberNames);
                    if (!match.matched()) {
                        continue;
                    }

                    String regionKey = world.getName().toLowerCase(Locale.ROOT) + ":" + region.getId().toLowerCase(Locale.ROOT);
                    if (!countedRegionKeys.add(regionKey)) {
                        continue;
                    }

                    long size = count3d ? calculate3d(region) : calculate2d(region);
                    if (size <= 0L) {
                        continue;
                    }

                    total += size;
                    report.matches.add(new TerritoryMatch(
                        world.getName(),
                        region.getId(),
                        count3d ? "3d" : "2d",
                        match.matchType(),
                        match.matchedValue(),
                        size
                    ));
                }
            }
        } catch (Exception exception) {
            report.error = exception.getMessage();
            report.finalValue = config.isTerritoryWorldGuardFallbackToManual() ? manualValue : 0;
            report.resolutionMode = config.isTerritoryWorldGuardFallbackToManual()
                ? "worldguard-error-fallback-manual"
                : "worldguard-error-zero";
            return report;
        }

        report.worldguardValue = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        report.finalValue = report.worldguardValue;
        report.resolutionMode = "worldguard";
        return report;
    }

    private Integer resolveViaWorldGuard(NationDefinition definition) {
        TerritoryDebugReport report = buildDebugReport(definition);

        if (report.resolutionMode.startsWith("worldguard-error")) {
            plugin.getLogger().warning("Failed to calculate WorldGuard territory for " + definition.slug() + ": " + report.error);
            return null;
        }

        if (report.resolutionMode.equals("worldguard-missing-fallback-manual")
            || report.resolutionMode.equals("worldguard-missing-zero")
            || report.resolutionMode.equals("no-members")) {
            return report.finalValue;
        }

        if (report.resolutionMode.equals("worldguard")) {
            return report.worldguardValue;
        }

        return report.finalValue;
    }

    private OwnershipMatch findOwnershipMatch(DefaultDomain owners, Set<UUID> memberUuids, Set<String> memberNames) {
        if (owners == null) {
            return OwnershipMatch.none();
        }

        Set<UUID> ownerUuids = owners.getUniqueIds();
        if (ownerUuids != null) {
            for (UUID uuid : ownerUuids) {
                if (memberUuids.contains(uuid)) {
                    return OwnershipMatch.uuid(uuid.toString());
                }
            }
        }

        Set<String> ownerNames = owners.getPlayers();
        if (ownerNames != null) {
            for (String name : ownerNames) {
                if (name != null && memberNames.contains(name.toLowerCase(Locale.ROOT))) {
                    return OwnershipMatch.name(name);
                }
            }
        }

        return OwnershipMatch.none();
    }

    private Set<UUID> resolveMemberUuids(List<String> members) {
        Set<UUID> result = new HashSet<>();
        for (String nickname : members) {
            if (nickname == null || nickname.isBlank()) {
                continue;
            }
            try {
                UUID uuid = Bukkit.getOfflinePlayer(nickname).getUniqueId();
                if (uuid != null) {
                    result.add(uuid);
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to resolve UUID for nation member " + nickname + ": " + exception.getMessage());
            }
        }
        return result;
    }

    private Set<String> resolveMemberNames(List<String> members) {
        Set<String> result = new HashSet<>();
        for (String nickname : members) {
            if (nickname == null || nickname.isBlank()) {
                continue;
            }
            result.add(nickname.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private long calculate2d(ProtectedRegion region) {
        long minX = region.getMinimumPoint().x();
        long maxX = region.getMaximumPoint().x();
        long minZ = region.getMinimumPoint().z();
        long maxZ = region.getMaximumPoint().z();

        long width = (maxX - minX) + 1L;
        long length = (maxZ - minZ) + 1L;
        if (width <= 0L || length <= 0L) {
            return 0L;
        }
        return width * length;
    }

    private long calculate3d(ProtectedRegion region) {
        long minX = region.getMinimumPoint().x();
        long maxX = region.getMaximumPoint().x();
        long minY = region.getMinimumPoint().y();
        long maxY = region.getMaximumPoint().y();
        long minZ = region.getMinimumPoint().z();
        long maxZ = region.getMaximumPoint().z();

        long width = (maxX - minX) + 1L;
        long height = (maxY - minY) + 1L;
        long length = (maxZ - minZ) + 1L;
        if (width <= 0L || height <= 0L || length <= 0L) {
            return 0L;
        }
        return width * height * length;
    }

    public static final class TerritoryDebugReport {
        public final String nationSlug;
        public final String source;
        public final int manualValue;
        public final String countMode;
        public final boolean fallbackToManual;
        public final List<TerritoryMatch> matches = new ArrayList<>();
        public int memberUuidsResolved;
        public int memberNamesResolved;
        public int worldguardValue;
        public int finalValue;
        public String resolutionMode;
        public String error;

        public TerritoryDebugReport(
            String nationSlug,
            String source,
            int manualValue,
            String countMode,
            boolean fallbackToManual
        ) {
            this.nationSlug = nationSlug;
            this.source = source;
            this.manualValue = manualValue;
            this.countMode = countMode;
            this.fallbackToManual = fallbackToManual;
        }
    }

    public record TerritoryMatch(
        String worldName,
        String regionId,
        String countMode,
        String matchType,
        String matchedValue,
        long contributedArea
    ) {}

    private record OwnershipMatch(boolean matched, String matchType, String matchedValue) {
        static OwnershipMatch none() {
            return new OwnershipMatch(false, null, null);
        }

        static OwnershipMatch uuid(String matchedValue) {
            return new OwnershipMatch(true, "uuid", matchedValue);
        }

        static OwnershipMatch name(String matchedValue) {
            return new OwnershipMatch(true, "name", matchedValue);
        }
    }
}
