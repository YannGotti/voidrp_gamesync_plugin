package ru.voidrp.gamesync.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record NationDefinition(
    String slug,
    String title,
    String leader,
    List<String> officers,
    List<String> members,
    int territoryPoints,
    int bossKills,
    int eventsCompleted,
    int prestigeBonus
) {
    public List<String> allMembersIncludingRoles() {
        Set<String> all = new LinkedHashSet<>();
        if (leader != null && !leader.isBlank()) {
            all.add(leader);
        }
        all.addAll(officers);
        all.addAll(members);
        return new ArrayList<>(all);
    }
}
