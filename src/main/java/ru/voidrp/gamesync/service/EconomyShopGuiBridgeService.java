package ru.voidrp.gamesync.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import ru.voidrp.gamesync.VoidRpGameSyncPlugin;
import ru.voidrp.gamesync.model.MarketPriceItem;
import ru.voidrp.gamesync.model.MarketTransactionPushRequest;

public final class EconomyShopGuiBridgeService {
    private static final String PRE_EVENT = "me.gypopo.economyshopgui.api.events.PreTransactionEvent";
    private static final String POST_EVENT = "me.gypopo.economyshopgui.api.events.PostTransactionEvent";

    private final VoidRpGameSyncPlugin plugin;
    private final Listener internalListener = new Listener() {};
    private boolean registered = false;
    private boolean economyShopGuiPresent = false;
    private long handledPreTransactions = 0L;
    private long pushedPostTransactions = 0L;
    private long skippedTransactions = 0L;
    private String lastError = "";

    public EconomyShopGuiBridgeService(VoidRpGameSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public void registerIfAvailable() {
        if (registered || !plugin.getGameSyncConfig().isEconomyMarketEnabled() || !plugin.getGameSyncConfig().isEconomyShopGuiBridgeEnabled()) {
            return;
        }

        Plugin esguiPlugin = Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
        if (esguiPlugin == null) {
            esguiPlugin = Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium");
        }
        economyShopGuiPresent = esguiPlugin != null;

        if (!economyShopGuiPresent) {
            plugin.getLogger().info("EconomyShopGUI not found. Dynamic shop bridge is disabled.");
            return;
        }

        try {
            ClassLoader apiClassLoader = esguiPlugin.getClass().getClassLoader();
            Class<?> preRaw = Class.forName(PRE_EVENT, true, apiClassLoader);
            Class<?> postRaw = Class.forName(POST_EVENT, true, apiClassLoader);

            if (!Event.class.isAssignableFrom(preRaw) || !Event.class.isAssignableFrom(postRaw)) {
                lastError = "EconomyShopGUI API classes are not Bukkit events.";
                plugin.getLogger().warning(lastError);
                return;
            }

            Class<? extends Event> preClass = (Class<? extends Event>) preRaw;
            Class<? extends Event> postClass = (Class<? extends Event>) postRaw;

            Bukkit.getPluginManager().registerEvent(
                    preClass,
                    internalListener,
                    EventPriority.HIGHEST,
                    (listener, event) -> {
                        try {
                            handlePreTransaction(event);
                        } catch (Throwable throwable) {
                            lastError = throwable.getMessage();
                            throw new EventException(throwable);
                        }
                    },
                    plugin,
                    true
            );

            Bukkit.getPluginManager().registerEvent(
                    postClass,
                    internalListener,
                    EventPriority.MONITOR,
                    (listener, event) -> {
                        try {
                            handlePostTransaction(event);
                        } catch (Throwable throwable) {
                            lastError = throwable.getMessage();
                            throw new EventException(throwable);
                        }
                    },
                    plugin,
                    true
            );

            registered = true;
            plugin.getLogger().info("EconomyShopGUI dynamic bridge registered.");
        } catch (ClassNotFoundException exception) {
            lastError = "EconomyShopGUI API events were not found: " + exception.getMessage();
            plugin.getLogger().warning(lastError);
        } catch (Throwable throwable) {
            lastError = throwable.getMessage();
            plugin.getLogger().warning("EconomyShopGUI bridge registration failed: " + throwable.getMessage());
        }
    }

    private void handlePreTransaction(Event event) throws Exception {
        if (!plugin.getGameSyncConfig().isEconomyMarketRealPriceEnabled()) {
            return;
        }

        Player player = (Player) call(event, "getPlayer");
        if (shouldIgnore(player)) {
            skippedTransactions++;
            return;
        }

        Map<?, ?> items = asMap(call(event, "getItems"));
        if (items != null && !items.isEmpty()) {
            // Sell-all/multi-item events expose only grouped prices per EcoType.
            // We do not rewrite them in Stage 3 to avoid wrong payouts; we still record them in PostTransactionEvent.
            return;
        }

        Object shopItem = call(event, "getShopItem");
        if (shopItem == null) {
            return;
        }

        ItemStack stack = itemToGive(shopItem);
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }

        String type = enumName(call(event, "getTransactionType"));
        boolean buy = isBuy(type);
        boolean sell = isSell(type);
        if (buy && !plugin.getGameSyncConfig().isEconomyMarketApplyBuyPrice()) return;
        if (sell && !plugin.getGameSyncConfig().isEconomyMarketApplySellPrice()) return;
        if (!buy && !sell) return;

        MarketPriceItem market = plugin.getEconomyMarketCache().get(stack.getType());
        if (market == null) {
            return;
        }

        int amount = Math.max(1, ((Number) call(event, "getAmount")).intValue());
        double unit = buy ? market.marketBuyPrice() : market.marketSellPrice();
        if (unit <= 0D) {
            return;
        }

        double originalTotal = number(call(event, "getOriginalPrice"));
        double targetTotal = round2(unit * amount);
        if (targetTotal < 0D) {
            return;
        }

        call(event, "setPrice", new Class<?>[]{double.class}, new Object[]{targetTotal});
        handledPreTransactions++;

        if (plugin.getGameSyncConfig().isEconomyMarketNotifyActionbar()) {
            String label = buy ? "покупки" : "продажи";
            sendActionBar(player, "§6VoidRP рынок: §fцена " + label + " §e" + money(targetTotal) + "§7 (" + stack.getType().name() + " x" + amount + ")");
        }
        if (plugin.getGameSyncConfig().isEconomyMarketNotifyChat() && Math.abs(targetTotal - originalTotal) >= 0.01D) {
            player.sendMessage("§7Рыночная цена применена: §f" + money(targetTotal) + " §8(было " + money(originalTotal) + ")");
        }
    }

    private void handlePostTransaction(Event event) throws Exception {
        if (!plugin.getGameSyncConfig().isEconomyMarketPushTransactions()) {
            return;
        }

        Player player = (Player) call(event, "getPlayer");
        if (shouldIgnore(player)) {
            skippedTransactions++;
            return;
        }

        String result = enumName(call(event, "getTransactionResult"));
        if (!isSuccessfulResult(result)) {
            return;
        }

        String type = enumName(call(event, "getTransactionType")).toLowerCase(Locale.ROOT);
        Map<?, ?> items = asMap(call(event, "getItems"));
        if (items != null && !items.isEmpty()) {
            if (!plugin.getGameSyncConfig().isEconomyMarketRecordMultiItemTransactions()) {
                return;
            }
            for (Map.Entry<?, ?> entry : items.entrySet()) {
                Object shopItem = entry.getKey();
                int amount = asInt(entry.getValue(), 0);
                if (amount <= 0) continue;
                ItemStack stack = itemToGive(shopItem);
                if (stack == null || stack.getType() == Material.AIR) continue;
                pushTransactionAsync(player, shopItem, stack, amount, type, 0D, 0D, 1D, true);
            }
            return;
        }

        Object shopItem = call(event, "getShopItem");
        if (shopItem == null) return;

        ItemStack stack = itemToGive(shopItem);
        if (stack == null || stack.getType() == Material.AIR) return;

        int amount = Math.max(1, ((Number) call(event, "getAmount")).intValue());
        double finalTotal = number(call(event, "getPrice"));

        double marketMultiplier = 1D;
        MarketPriceItem market = plugin.getEconomyMarketCache().get(stack.getType());
        if (market != null) {
            if (isBuy(type) && market.base_buy_price > 0D) {
                marketMultiplier = market.current_buy_price / market.base_buy_price;
            } else if (isSell(type) && market.base_sell_price > 0D) {
                marketMultiplier = market.current_sell_price / market.base_sell_price;
            }
        }

        pushTransactionAsync(player, shopItem, stack, amount, type, finalTotal, finalTotal, marketMultiplier, false);
    }

    private void pushTransactionAsync(
            Player player,
            Object shopItem,
            ItemStack stack,
            int amount,
            String transactionType,
            double baseTotal,
            double finalTotal,
            double multiplier,
            boolean multiItemApproximation
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "economyshopgui");
        metadata.put("transaction_type_raw", transactionType);
        metadata.put("multi_item_approximation", multiItemApproximation);
        metadata.put("shop_item_path", safeString(callQuiet(shopItem, "getItemPath")));

        String section = safeString(fieldQuiet(shopItem, "section"));
        String itemLoc = safeString(fieldQuiet(shopItem, "itemLoc"));
        metadata.put("shop_section", section);
        metadata.put("shop_item_index", itemLoc);

        MarketTransactionPushRequest request = new MarketTransactionPushRequest(
                player.getName(),
                stack.getType().name(),
                amount,
                transactionType,
                round2(baseTotal),
                round2(finalTotal),
                round6(multiplier <= 0D ? 1D : multiplier),
                displayName(stack),
                emptyToNull(section),
                emptyToNull(itemLoc),
                "economyshopgui",
                metadata
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getBackendClient().pushMarketTransaction(request);
                pushedPostTransactions++;
                if (plugin.getGameSyncConfig().isVerboseSync()) {
                    plugin.getLogger().info("Pushed ESGUI transaction: " + request.material() + " x" + request.amount() + " " + request.transaction_type());
                }
            } catch (Exception exception) {
                lastError = exception.getMessage();
                if (plugin.getGameSyncConfig().isVerboseSync()) {
                    plugin.getLogger().warning("Failed to push ESGUI transaction: " + exception.getMessage());
                }
            }
        });
    }

    private boolean shouldIgnore(Player player) {
        if (player == null) return true;
        if (plugin.getGameSyncConfig().isEconomyMarketIgnoreOpPlayers() && player.isOp()) return true;
        return plugin.getGameSyncConfig().isEconomyMarketIgnoreCreativePlayers()
                && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR);
    }

    private ItemStack itemToGive(Object shopItem) {
        Object value = callQuiet(shopItem, "getItemToGive");
        if (value instanceof ItemStack stack) return stack;
        value = callQuiet(shopItem, "getShopItem");
        if (value instanceof ItemStack stack) return stack;
        return null;
    }

    private String displayName(ItemStack stack) {
        if (stack == null) return "";
        if (stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        return stack.getType().name();
    }

    private boolean isBuy(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return value.contains("buy") && !value.contains("sell");
    }

    private boolean isSell(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return value.contains("sell");
    }

    private boolean isSuccessfulResult(String result) {
        String value = result == null ? "" : result.toUpperCase(Locale.ROOT);
        return value.startsWith("SUCCESS") || value.equals("NOT_ALL_ITEMS_ADDED");
    }

    private void sendActionBar(Player player, String message) {
        try {
            Method method = player.getClass().getMethod("sendActionBar", String.class);
            method.invoke(player, message);
        } catch (Exception ignored) {
            player.sendMessage(message);
        }
    }

    private Object call(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private Object call(Object target, String method, Class<?>[] types, Object[] args) throws Exception {
        Method m = target.getClass().getMethod(method, types);
        return m.invoke(target, args);
    }

    private Object callQuiet(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object fieldQuiet(Object target, String field) {
        if (target == null) return null;
        try {
            Field f = target.getClass().getField(field);
            return f.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private String enumName(Object value) {
        if (value instanceof Enum<?> e) return e.name();
        return value == null ? "" : String.valueOf(value);
    }

    private double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0D;
    }

    private int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0D) / 1_000_000.0D;
    }

    private String money(double value) {
        return String.format(Locale.US, "%.2f", round2(value));
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean isEconomyShopGuiPresent() {
        return economyShopGuiPresent;
    }

    public long getHandledPreTransactions() {
        return handledPreTransactions;
    }

    public long getPushedPostTransactions() {
        return pushedPostTransactions;
    }

    public long getSkippedTransactions() {
        return skippedTransactions;
    }

    public String getLastError() {
        return lastError;
    }
}
