package ru.voidrp.gamesync.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.HashSet;
import java.util.List;
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

    private Integer resolveViaWorldGuard(NationDefinition definition) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return null;
        }

        Set<UUID> memberUuids = resolveMemberUuids(definition.allMembersIncludingRoles());
        if (memberUuids.isEmpty()) {
            return 0;
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
                    Set<UUID> owners = region.getOwners().getUniqueIds();
                    if (owners == null || owners.isEmpty()) {
                        continue;
                    }

                    boolean ownedByNationMember = false;
                    for (UUID uuid : owners) {
                        if (memberUuids.contains(uuid)) {
                            ownedByNationMember = true;
                            break;
                        }
                    }

                    if (!ownedByNationMember) {
                        continue;
                    }

                    String regionKey = world.getName().toLowerCase() + ":" + region.getId().toLowerCase();
                    if (!countedRegionKeys.add(regionKey)) {
                        continue;
                    }

                    long size = count3d ? calculate3d(region) : calculate2d(region);
                    if (size > 0L) {
                        total += size;
                    }
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to calculate WorldGuard territory for " + definition.slug() + ": " + exception.getMessage());
            return null;
        }

        if (total > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) total;
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
}
