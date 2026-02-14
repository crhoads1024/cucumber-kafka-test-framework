package com.enterprise.testing.datagen.market;

import com.enterprise.testing.shared.model.trade.MarketSnapshot;
import com.enterprise.testing.shared.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches REAL market data from Yahoo Finance to seed test data generators.
 *
 * WHY YAHOO FINANCE:
 * - TradingView has no public API (confirmed by their own support page)
 * - Yahoo Finance has free, unauthenticated endpoints for quotes
 * - The sstrickx/yahoofinance-api Java library wraps these nicely,
 *   but we use the raw HTTP endpoint here to avoid an extra dependency
 *   and to demonstrate the pattern clearly
 *
 * DATA FLOW:
 *   Yahoo Finance API  -->  MarketDataProvider (caches snapshots)
 *                               |
 *                               v
 *                       TradeGenerator (uses real prices)
 *                               |
 *                               v
 *                       SettlementGenerator (derives from trades)
 *
 * CACHING:
 * - Snapshots are cached for the configured TTL (default 5 minutes)
 * - In CI/CD, you can pre-fetch and persist to disk so tests
 *   don't depend on network access during execution
 *
 * FALLBACK:
 * - If Yahoo Finance is unreachable, falls back to hardcoded
 *   reference data so tests never fail due to network issues
 */
public class MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(MarketDataProvider.class);

    // Yahoo Finance v8 quote endpoint (free, no auth required)
    private static final String YAHOO_QUOTE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d";

    // Cache with TTL
    private final Map<String, CachedSnapshot> cache = new ConcurrentHashMap<>();
    private final long cacheTtlMs;
    private final HttpClient httpClient;

    // Default watchlist - symbols you're actively trading
    public static final List<String> DEFAULT_SYMBOLS = List.of(
            "BTC-USD", "XRP-USD", "CRV-USD", "LINK-USD", "ADA-USD",  // Crypto
            "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA",                 // Tech
            "JPM", "BAC", "GS",                                       // Financials
            "SPY", "QQQ"                                               // ETFs
    );

    public MarketDataProvider() {
        this(Duration.ofMinutes(5));
    }

    public MarketDataProvider(Duration cacheTtl) {
        this.cacheTtlMs = cacheTtl.toMillis();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetch a market snapshot for a single symbol.
     * Returns cached data if fresh, otherwise fetches from Yahoo Finance.
     */
    public MarketSnapshot getSnapshot(String symbol) {
        CachedSnapshot cached = cache.get(symbol);
        if (cached != null && !cached.isExpired(cacheTtlMs)) {
            log.debug("Cache hit for {}", symbol);
            return cached.snapshot;
        }

        try {
            MarketSnapshot snapshot = fetchFromYahoo(symbol);
            cache.put(symbol, new CachedSnapshot(snapshot));
            log.info("Fetched live market data for {}: ${}", symbol, snapshot.getPrice());
            return snapshot;
        } catch (Exception e) {
            log.warn("Failed to fetch live data for {}. Using fallback. Error: {}", symbol, e.getMessage());
            return getFallbackSnapshot(symbol);
        }
    }

    /**
     * Fetch snapshots for multiple symbols.
     */
    public Map<String, MarketSnapshot> getSnapshots(List<String> symbols) {
        Map<String, MarketSnapshot> snapshots = new LinkedHashMap<>();
        for (String symbol : symbols) {
            snapshots.put(symbol, getSnapshot(symbol));
            // Rate limiting: small delay between requests
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        return snapshots;
    }

    /**
     * Fetch all default watchlist symbols.
     */
    public Map<String, MarketSnapshot> getDefaultSnapshots() {
        return getSnapshots(DEFAULT_SYMBOLS);
    }

    /**
     * Persist snapshots to disk for offline/CI usage.
     */
    public void persistSnapshots(Map<String, MarketSnapshot> snapshots, java.nio.file.Path outputPath) {
        JsonUtil.writeToFile(snapshots, outputPath);
        log.info("Persisted {} market snapshots to {}", snapshots.size(), outputPath);
    }

    /**
     * Load previously persisted snapshots (for CI/CD where network may not be available).
     */
    public Map<String, MarketSnapshot> loadSnapshots(java.nio.file.Path inputPath) {
        return JsonUtil.readFromFile(inputPath,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, MarketSnapshot>>() {});
    }

    // ===== YAHOO FINANCE INTEGRATION =====

    private MarketSnapshot fetchFromYahoo(String symbol) throws IOException, InterruptedException {
        String url = String.format(YAHOO_QUOTE_URL, symbol);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Yahoo Finance returned " + response.statusCode() + " for " + symbol);
        }

        return parseYahooResponse(symbol, response.body());
    }

    /**
     * Parse Yahoo Finance v8 chart API response.
     *
     * Response structure:
     * {
     *   "chart": {
     *     "result": [{
     *       "meta": {
     *         "symbol": "AAPL",
     *         "regularMarketPrice": 227.63,
     *         "previousClose": 225.0,
     *         ...
     *       },
     *       "indicators": {
     *         "quote": [{
     *           "open": [...], "high": [...], "low": [...],
     *           "close": [...], "volume": [...]
     *         }]
     *       }
     *     }]
     *   }
     * }
     */
    private MarketSnapshot parseYahooResponse(String symbol, String responseBody) {
        JsonNode root = JsonUtil.fromJson(responseBody, JsonNode.class);
        JsonNode meta = root.path("chart").path("result").get(0).path("meta");

        MarketSnapshot snapshot = new MarketSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setExchange(getTextOrNull(meta, "exchangeName"));
        snapshot.setName(getTextOrNull(meta, "shortName"));
        snapshot.setPrice(getDecimalOrNull(meta, "regularMarketPrice"));
        snapshot.setPreviousClose(getDecimalOrNull(meta, "chartPreviousClose"));
        snapshot.setDayHigh(getDecimalOrNull(meta, "regularMarketDayHigh"));
        snapshot.setDayLow(getDecimalOrNull(meta, "regularMarketDayLow"));
        snapshot.setVolume(getLongOrZero(meta, "regularMarketVolume"));

        // Derive bid/ask from price (Yahoo free tier doesn't always include these)
        if (snapshot.getPrice() != null) {
            BigDecimal spreadPct = estimateSpread(symbol, snapshot.getVolume());
            BigDecimal halfSpread = snapshot.getPrice().multiply(spreadPct)
                    .divide(BigDecimal.valueOf(2), 4, java.math.RoundingMode.HALF_UP);
            snapshot.setBid(snapshot.getPrice().subtract(halfSpread));
            snapshot.setAsk(snapshot.getPrice().add(halfSpread));
        }

        snapshot.setSnapshotTime(Instant.now());
        return snapshot;
    }

    /**
     * Estimate realistic bid-ask spread based on liquidity.
     * High-volume stocks: ~0.01-0.03%
     * Low-volume stocks: ~0.1-0.5%
     * Crypto: ~0.05-0.2%
     */
    private BigDecimal estimateSpread(String symbol, long volume) {
        if (symbol.endsWith("-USD")) {
            // Crypto - wider spreads
            return new BigDecimal("0.001");
        }
        if (volume > 10_000_000) {
            return new BigDecimal("0.0002"); // Very liquid
        }
        if (volume > 1_000_000) {
            return new BigDecimal("0.0005"); // Liquid
        }
        return new BigDecimal("0.002"); // Less liquid
    }

    // ===== FALLBACK DATA =====

    /**
     * Hardcoded fallback data so tests never break due to network issues.
     * These are approximate reference prices — the exact values don't matter
     * for test logic, only that they're in a realistic range.
     */
    private MarketSnapshot getFallbackSnapshot(String symbol) {
        MarketSnapshot snapshot = new MarketSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setSnapshotTime(Instant.now());

        // Approximate reference prices (update periodically)
        Map<String, String[]> fallbackData = Map.ofEntries(
                Map.entry("BTC-USD",  new String[]{"97000.00", "CCC", "Bitcoin USD"}),
                Map.entry("XRP-USD",  new String[]{"2.45", "CCC", "XRP USD"}),
                Map.entry("CRV-USD",  new String[]{"0.55", "CCC", "Curve DAO Token USD"}),
                Map.entry("LINK-USD", new String[]{"18.50", "CCC", "Chainlink USD"}),
                Map.entry("ADA-USD",  new String[]{"0.78", "CCC", "Cardano USD"}),
                Map.entry("AAPL",     new String[]{"228.00", "NMS", "Apple Inc."}),
                Map.entry("MSFT",     new String[]{"415.00", "NMS", "Microsoft Corporation"}),
                Map.entry("GOOGL",    new String[]{"175.00", "NMS", "Alphabet Inc."}),
                Map.entry("AMZN",     new String[]{"205.00", "NMS", "Amazon.com Inc."}),
                Map.entry("TSLA",     new String[]{"350.00", "NMS", "Tesla Inc."}),
                Map.entry("JPM",      new String[]{"255.00", "NYQ", "JPMorgan Chase & Co."}),
                Map.entry("BAC",      new String[]{"44.00", "NYQ", "Bank of America Corp."}),
                Map.entry("GS",       new String[]{"595.00", "NYQ", "Goldman Sachs Group"}),
                Map.entry("SPY",      new String[]{"600.00", "PCX", "SPDR S&P 500 ETF"}),
                Map.entry("QQQ",      new String[]{"520.00", "NMS", "Invesco QQQ Trust"})
        );

        String[] data = fallbackData.getOrDefault(symbol,
                new String[]{"100.00", "UNK", symbol});

        snapshot.setPrice(new BigDecimal(data[0]));
        snapshot.setExchange(data[1]);
        snapshot.setName(data[2]);
        snapshot.setPreviousClose(snapshot.getPrice().multiply(new BigDecimal("0.995")));
        snapshot.setBid(snapshot.getPrice().multiply(new BigDecimal("0.9998")));
        snapshot.setAsk(snapshot.getPrice().multiply(new BigDecimal("1.0002")));
        snapshot.setVolume(1_000_000L);

        log.info("Using fallback data for {}: ${}", symbol, snapshot.getPrice());
        return snapshot;
    }

    // ===== JSON HELPERS =====

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private BigDecimal getDecimalOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? new BigDecimal(child.asText()) : null;
    }

    private long getLongOrZero(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asLong() : 0L;
    }

    // ===== CACHE HELPER =====

    private static class CachedSnapshot {
        final MarketSnapshot snapshot;
        final long timestamp;

        CachedSnapshot(MarketSnapshot snapshot) {
            this.snapshot = snapshot;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }
}
