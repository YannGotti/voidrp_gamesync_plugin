package ru.voidrp.gamesync.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import ru.voidrp.gamesync.model.NationDefinition;
import ru.voidrp.gamesync.util.NameNormalizer;

public final class NationRegistry {

    private final Map<String, NationDefinition> nations = new TreeMap<>();

    public NationRegistry(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "nations.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = yaml.getConfigurationSection("nations");
        if (root == null) {
            return;
        }

        for (String slugKey : root.getKeys(false)) {
            ConfigurationSection nationSection = root.getConfigurationSection(slugKey);
            if (nationSection == null) {
                continue;
            }

            String slug = slugKey.toLowerCase(Locale.ROOT);
            String title = nationSection.getString("title", slug);
            String leader = normalizeOrNull(nationSection.getString("leader"));
            List<String> officers = normalizeList(nationSection.getStringList("officers"));
            List<String> members = normalizeList(nationSection.getStringList("members"));

            int territoryPoints = nationSection.getInt("manual.territory-points", 0);
            int bossKills = nationSection.getInt("manual.boss-kills", 0);
            int eventsCompleted = nationSection.getInt("manual.events-completed", 0);
            int prestigeBonus = nationSection.getInt("manual.prestige-bonus", 0);

            NationDefinition definition = new NationDefinition(
                slug,
                title,
                leader,
                officers,
                members,
                territoryPoints,
                bossKills,
                eventsCompleted,
                prestigeBonus
            );
            nations.put(slug, definition);
        }
    }

    public List<NationDefinition> all() {
        return new ArrayList<>(nations.values());
    }

    public NationDefinition get(String slug) {
        if (slug == null) {
            return null;
        }
        return nations.get(slug.toLowerCase(Locale.ROOT));
    }

    private String normalizeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return NameNormalizer.normalizeMinecraftName(value);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(NameNormalizer.normalizeMinecraftName(value));
        }
        return result;
    }
}
