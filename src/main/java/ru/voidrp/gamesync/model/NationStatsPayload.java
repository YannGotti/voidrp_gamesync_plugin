package ru.voidrp.gamesync.model;

public record NationStatsPayload(
    String nationSlug,
    double treasuryBalance,
    int territoryPoints,
    int totalPlaytimeMinutes,
    int pvpKills,
    int mobKills,
    int bossKills,
    int deaths,
    long blocksPlaced,
    long blocksBroken,
    int eventsCompleted,
    int prestigeScore
) {
}
