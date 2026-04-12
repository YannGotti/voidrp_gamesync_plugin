package ru.voidrp.gamesync.store;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginDataStore {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public PluginDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public String getRewardBundle(UUID playerId) {
        return yaml.getString("reward-cache." + playerId + ".bundle");
    }

    public String getRewardExpiresAt(UUID playerId) {
        return yaml.getString("reward-cache." + playerId + ".expires-at");
    }

    public void setRewardGrant(UUID playerId, String bundleKey, String expiresAt) {
        yaml.set("reward-cache." + playerId + ".bundle", bundleKey);
        yaml.set("reward-cache." + playerId + ".expires-at", expiresAt);
    }

    public int getNationOverride(String slug, String key) {
        return yaml.getInt("nation-overrides." + slug + "." + key, 0);
    }

    public void setNationOverride(String slug, String key, int value) {
        yaml.set("nation-overrides." + slug + "." + key, value);
    }

    public void saveNow() {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save data.yml: " + exception.getMessage());
        }
    }
}
