package ru.voidrp.gamesync.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ru.voidrp.gamesync.config.GameSyncConfig;
import ru.voidrp.gamesync.model.GameNationDonationRequest;
import ru.voidrp.gamesync.model.GameNationListResponse;
import ru.voidrp.gamesync.model.GameNationTreasuryWithdrawRequest;
import ru.voidrp.gamesync.model.MarketPriceItem;
import ru.voidrp.gamesync.model.MarketPriceSnapshotResponse;
import ru.voidrp.gamesync.model.MarketTransactionPushRequest;
import ru.voidrp.gamesync.model.NationDefinition;
import ru.voidrp.gamesync.model.NationMarketCancelRequest;
import ru.voidrp.gamesync.model.NationMarketCancelResponse;
import ru.voidrp.gamesync.model.NationMarketCreateRequest;
import ru.voidrp.gamesync.model.NationMarketListing;
import ru.voidrp.gamesync.model.NationMarketListingListResponse;
import ru.voidrp.gamesync.model.NationMarketPurchaseRequest;
import ru.voidrp.gamesync.model.NationMarketPurchaseResponse;
import ru.voidrp.gamesync.model.NationMemberStatsSyncRequest;
import ru.voidrp.gamesync.model.NationStatsPayload;
import ru.voidrp.gamesync.model.PlayerSkinResponse;
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

    public GameNationListResponse fetchNationDefinitions() throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/nations");
        HttpResponse<String> response = get(url);
        return gson.fromJson(response.body(), GameNationListResponse.class);
    }

    public void upsertNationStats(NationStatsPayload payload) throws IOException, InterruptedException {
        String url = apiUrl("/nation-stats/internal/upsert");
        postJson(url, gson.toJson(payload), "Nation stats upsert failed");
    }

    public void upsertNationMemberSnapshots(NationMemberStatsSyncRequest payload) throws IOException, InterruptedException {
        String url = apiUrl("/nation-stats/internal/member-snapshots/upsert");
        postJson(url, gson.toJson(payload), "Nation member snapshot sync failed");
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

        postJson(url, json, "Nation membership sync failed");
    }

    public void donateToNationTreasury(GameNationDonationRequest payload) throws IOException, InterruptedException {
        String url = apiUrl("/nation-stats/internal/player-donate");
        postJson(url, gson.toJson(payload), "Nation treasury donation failed");
    }

    public NationTreasuryActionResponse withdrawFromNationTreasury(GameNationTreasuryWithdrawRequest payload)
            throws IOException, InterruptedException {

        String url = apiUrl("/nation-stats/internal/player-withdraw");
        HttpResponse<String> response = postJsonForResponse(
                url,
                gson.toJson(payload),
                "Nation treasury withdraw failed"
        );
        return gson.fromJson(response.body(), NationTreasuryActionResponse.class);
    }

    public NationTreasurySummaryResponse getNationTreasurySummary(String slug) throws IOException, InterruptedException {
        String url = apiUrl("/nation-stats/internal/nations/" + encode(slug) + "/summary");
        HttpResponse<String> response = get(url);
        return gson.fromJson(response.body(), NationTreasurySummaryResponse.class);
    }

    public NationTreasuryTransactionListResponse getNationTreasuryTransactions(String slug) throws IOException, InterruptedException {
        String url = apiUrl("/nation-stats/internal/nations/" + encode(slug) + "/transactions");
        HttpResponse<String> response = get(url);
        return gson.fromJson(response.body(), NationTreasuryTransactionListResponse.class);
    }

    public PlayerSkinResponse getPlayerSkin(String minecraftNickname) throws IOException, InterruptedException {
        String path = "/server/auth/player-skin/" + encode(minecraftNickname);
        String url = apiUrl(path);
        HttpResponse<String> response = get(url);
        return gson.fromJson(response.body(), PlayerSkinResponse.class);
    }

    public ReferralResolveResponse resolveReferralReward(String minecraftNickname) throws IOException, InterruptedException {
        String path = "/game-sync/referrals/reward/" + encode(minecraftNickname);
        String url = apiUrl(path);
        HttpResponse<String> response = get(url);
        return gson.fromJson(response.body(), ReferralResolveResponse.class);
    }

    public MarketPriceSnapshotResponse fetchMarketPrices() throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/economy/prices");
        HttpResponse<String> response = get(url);
        return gson.fromJson(response.body(), MarketPriceSnapshotResponse.class);
    }

    public MarketPriceItem getMarketPrice(String material) throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/economy/prices/" + encode(material));
        HttpResponse<String> response = get(url);
        return gson.fromJson(response.body(), MarketPriceItem.class);
    }

    public void pushMarketTransaction(MarketTransactionPushRequest payload) throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/economy/transactions");
        postJson(url, gson.toJson(payload), "Market transaction push failed");
    }

    public MarketRecalculateResponse recalculateMarketPrices(boolean decayScores) throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/economy/recalculate?decay_scores=" + decayScores);
        HttpResponse<String> response = postJsonForResponse(url, "{}", "Market recalculation failed");
        return gson.fromJson(response.body(), MarketRecalculateResponse.class);
    }

    public NationMarketListing createNationMarketListing(NationMarketCreateRequest payload) throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/nation-market/listings");
        HttpResponse<String> response = postJsonForResponse(url, gson.toJson(payload), "Nation market listing create failed");
        return gson.fromJson(response.body(), NationMarketListing.class);
    }

    public NationMarketListingListResponse listNationMarketListings() throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/nation-market/listings");
        HttpResponse<String> response = get(url);
        return gson.fromJson(response.body(), NationMarketListingListResponse.class);
    }

    public NationMarketListingListResponse listNationMarketListings(String nationSlug, boolean includeInactive) throws IOException, InterruptedException {
        String path = "/game-sync/nation-market/listings?nation_slug=" + encode(nationSlug) + "&include_inactive=" + includeInactive;
        HttpResponse<String> response = get(apiUrl(path));
        return gson.fromJson(response.body(), NationMarketListingListResponse.class);
    }

    public NationMarketPurchaseResponse purchaseNationMarketListing(String listingId, NationMarketPurchaseRequest payload)
            throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/nation-market/listings/" + encode(listingId) + "/purchase");
        HttpResponse<String> response = postJsonForResponse(url, gson.toJson(payload), "Nation market purchase failed");
        return gson.fromJson(response.body(), NationMarketPurchaseResponse.class);
    }

    public NationMarketCancelResponse cancelNationMarketListing(String listingId, NationMarketCancelRequest payload)
            throws IOException, InterruptedException {
        String url = apiUrl("/game-sync/nation-market/listings/" + encode(listingId) + "/cancel");
        HttpResponse<String> response = postJsonForResponse(url, gson.toJson(payload), "Nation market cancel failed");
        return gson.fromJson(response.body(), NationMarketCancelResponse.class);
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
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
            throw new IOException("GET failed with status " + response.statusCode() + ": " + response.body());
        }

        return response;
    }

    private void postJson(String url, String json, String messagePrefix) throws IOException, InterruptedException {
        postJsonForResponse(url, json, messagePrefix);
    }

    private HttpResponse<String> postJsonForResponse(String url, String json, String messagePrefix)
            throws IOException, InterruptedException {

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
            throw new IOException(messagePrefix + " with status " + response.statusCode() + ": " + response.body());
        }

        return response;
    }

    private String apiUrl(String path) {
        String prefix = config.getApiPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return config.getBackendBaseUrl() + prefix + path;
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

    public static final class MarketRecalculateResponse {
        public int total;
        public int changed;
    }

    public static final class NationTreasuryActionResponse {
        public String message;
        public String nation_slug;
        public double new_treasury_balance;
    }

    public static final class NationTreasurySummaryResponse {
        public String nation_id;
        public double treasury_balance;
        public int territory_points;
        public int total_playtime_minutes;
        public int pvp_kills;
        public int mob_kills;
        public int boss_kills;
        public int deaths;
        public long blocks_placed;
        public long blocks_broken;
        public int events_completed;
        public int prestige_score;
        public String updated_at;
    }

    public static final class NationTreasuryTransactionListResponse {
        public int total;
        public java.util.List<NationTreasuryTransactionItem> items;
    }

    public static final class NationTreasuryTransactionItem {
        public String id;
        public String transaction_type;
        public double gross_amount;
        public double fee_amount;
        public double net_amount;
        public String comment;
        public java.util.Map<String, Object> metadata_json;
        public String created_at;
    }
}
