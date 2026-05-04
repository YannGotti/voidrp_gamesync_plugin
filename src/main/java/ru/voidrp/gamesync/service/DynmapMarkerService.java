package ru.voidrp.gamesync.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import ru.voidrp.gamesync.model.NationDefinition;

public final class DynmapMarkerService {

    private static final String MARKER_SET_ID = "voidrp_nations";
    private static final String MARKER_SET_LABEL = "Государства";
    private static final String MARKER_PREFIX = "nation_capital_";
    private static final String SITE_BASE = "https://void-rp.ru/nation/";

    private final JavaPlugin plugin;
    private MarkerAPI markerApi;
    private MarkerSet markerSet;

    public DynmapMarkerService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmap == null || !dynmap.isEnabled()) {
            plugin.getLogger().info("[DynmapMarkers] Dynmap not available, markers disabled.");
            return;
        }
        DynmapAPI api = (DynmapAPI) dynmap;
        this.markerApi = api.getMarkerAPI();
        if (markerApi == null) {
            plugin.getLogger().warning("[DynmapMarkers] MarkerAPI not available.");
            return;
        }
        this.markerSet = markerApi.getMarkerSet(MARKER_SET_ID);
        if (markerSet == null) {
            this.markerSet = markerApi.createMarkerSet(MARKER_SET_ID, MARKER_SET_LABEL, null, false);
        } else {
            markerSet.setMarkerSetLabel(MARKER_SET_LABEL);
        }
        plugin.getLogger().info("[DynmapMarkers] Initialized.");
    }

    public boolean isAvailable() {
        return markerSet != null;
    }

    public void updateMarkers(List<NationDefinition> nations) {
        if (!isAvailable()) return;

        Set<String> activeIds = new HashSet<>();

        for (NationDefinition nation : nations) {
            if (nation.capitalX() == null || nation.capitalZ() == null) continue;

            String markerId = MARKER_PREFIX + nation.slug();
            activeIds.add(markerId);

            String world = nation.capitalWorld() != null ? nation.capitalWorld() : "world";
            double x = nation.capitalX();
            double z = nation.capitalZ();
            String label = buildLabel(nation);

            Marker existing = markerSet.findMarker(markerId);
            if (existing != null) {
                existing.setLabel(label, true);
                existing.setLocation(world, x, 64, z);
            } else {
                markerSet.createMarker(
                        markerId, label, true,
                        world, x, 64, z,
                        markerApi.getMarkerIcon("world"), false
                );
            }
        }

        // Remove stale markers for nations that no longer have capitals
        for (Marker marker : markerSet.getMarkers()) {
            if (marker.getMarkerID().startsWith(MARKER_PREFIX)
                    && !activeIds.contains(marker.getMarkerID())) {
                marker.deleteMarker();
            }
        }
    }

    private String buildLabel(NationDefinition nation) {
        String color = nation.accentColor() != null ? nation.accentColor() : "#7c3aed";
        String leader = nation.leader() != null ? nation.leader() : "—";
        return "<div style='font-weight:bold;color:" + color + "'>[" + nation.tag() + "] " + nation.title() + "</div>"
                + "<div style='color:#aaa;font-size:90%'>Лидер: " + leader + "</div>"
                + "<div style='margin-top:4px'><a href='" + SITE_BASE + nation.slug()
                + "' target='_blank' style='color:#a78bfa;font-size:88%'>→ Государство на сайте</a></div>";
    }
}
