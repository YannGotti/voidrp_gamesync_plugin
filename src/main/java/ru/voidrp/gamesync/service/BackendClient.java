package ru.voidrp.gamesync.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.bukkit.plugin.java.JavaPlugin;

import ru.voidrp.gamesync.config.GameSyncConfig;
import ru.voidrp.gamesync.model.NationDefinition;
import ru.voidrp.gamesync.model.NationStatsPayload;
import ru.voidrp.gamesync.model.ReferralResolveResponse;

public final class BackendClient {

    private final JavaPlugin plugin;
    private final GameSyncConfig config;
    private final HttpClient httpClient;
    private final Gson gson;

    public BackendClient(JavaPlugin plugin, GameSyncConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
            .build();
        this.gson = new GsonBuilder().create();
    }

    public void upsertNationStats(NationStatsPayload payload) throws IOException, InterruptedException {
        String url = apiUrl("/nation-stats/internal/upsert");
        String json = gson.toJson(payload);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
            .header("Content-Type", "application/json")
            .header("X-Game-Auth-Secret", config.getGameAuthSecret())
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (config.isDebugHttp()) {
            plugin.getLogger().info("[HTTP] POST " + url + " -> " + response.statusCode() + " body=" + response.body());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Nation stats upsert failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    public void syncNationMembership(NationDefinition definition) throws IOException, InterruptedException {
        String path = "/game-sync/nations/" + encode(definition.slug()) + "/membership";
        String url = apiUrl(path);

        String json = gson.toJson(new MembershipRequest(
            definition.leader(),
            definition.officers(),
            definition.members(),
            true
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
            .header("Content-Type", "application/json")
            .header("X-Game-Auth-Secret", config.getGameAuthSecret())
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (config.isDebugHttp()) {
            plugin.getLogger().info("[HTTP] POST " + url + " -> " + response.statusCode() + " body=" + response.body());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Nation membership sync failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    public ReferralResolveResponse resolveReferralReward(String minecraftNickname) throws IOException, InterruptedException {
        String path = "/game-sync/referrals/reward/" + encode(minecraftNickname);
        String url = apiUrl(path);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
            .header("X-Game-Auth-Secret", config.getGameAuthSecret())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (config.isDebugHttp()) {
            plugin.getLogger().info("[HTTP] GET " + url + " -> " + response.statusCode() + " body=" + response.body());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Referral resolve failed with status " + response.statusCode() + ": " + response.body());
        }

        return gson.fromJson(response.body(), ReferralResolveResponse.class);
    }

    private String apiUrl(String path) {
        return config.getBackendBaseUrl() + config.getApiPrefix() + path;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record MembershipRequest(
        String leader_minecraft_nickname,
        java.util.List<String> officers,
        java.util.List<String> members,
        boolean replace_missing
    ) {}
}
