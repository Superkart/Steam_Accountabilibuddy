package com.cs484.steamaccountibilibuddy.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for making batch requests to Steam's IStoreBrowseService API.
 * This is much more efficient than individual API calls, especially for price checking.
 * Can handle up to 700 games per request.
 */
@Service
public class SteamBatchService {
    private final WebClient webClient;

    public SteamBatchService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Batch fetch store items (prices, names, etc.) for multiple app IDs using Steam's JSON API.
     * Much more efficient than individual requests - can handle up to 700 games per request.
     *
     * @param appIds List of Steam app IDs to fetch (recommend batches of 100-500)
     * @param countryCode Country code for pricing (e.g., "US", "GB")
     * @return Map of appId to price in BigDecimal, or empty map if fetch fails
     */
    public Map<Integer, BigDecimal> batchGetPrices(List<Integer> appIds, String countryCode) {
        if (appIds == null || appIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // Build JSON request for Steam API
            String jsonRequest = buildJsonRequest(appIds, countryCode);

            // Build URL with properly encoded JSON parameter
            String urlEncodedJson = java.net.URLEncoder.encode(jsonRequest, java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json=" + urlEncodedJson;

            // Make GET request to Steam's public IStoreBrowseService API
            String responseJson = webClient.get()
                    .uri(java.net.URI.create(url))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null) {
                return Collections.emptyMap();
            }

            // Parse JSON response using Jackson
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseJson);
            com.fasterxml.jackson.databind.JsonNode responseNode = rootNode.get("response");

            if (responseNode == null || !responseNode.has("store_items")) {
                return Collections.emptyMap();
            }

            // Extract prices from JSON response
            Map<Integer, BigDecimal> priceMap = new HashMap<>();
            com.fasterxml.jackson.databind.JsonNode storeItems = responseNode.get("store_items");

            for (com.fasterxml.jackson.databind.JsonNode item : storeItems) {
                if (!item.has("appid")) continue;

                int appId = item.get("appid").asInt();

                // Check if game is free
                if (item.has("is_free") && item.get("is_free").asBoolean()) {
                    priceMap.put(appId, BigDecimal.ZERO);
                    continue;
                }

                // Get price from best_purchase_option
                if (item.has("best_purchase_option")) {
                    com.fasterxml.jackson.databind.JsonNode purchaseOption = item.get("best_purchase_option");
                    if (purchaseOption.has("final_price_in_cents")) {
                        // Price is in cents, convert to dollars
                        long priceCents = purchaseOption.get("final_price_in_cents").asLong();
                        BigDecimal price = BigDecimal.valueOf(priceCents).divide(BigDecimal.valueOf(100));
                        priceMap.put(appId, price);
                    }
                }
            }

            return priceMap;

        } catch (Exception e) {
            System.err.println("Batch price fetch failed: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Build JSON request payload for Steam IStoreBrowseService API.
     * Matches the structure expected by Steam's JSON API endpoint.
     *
     * @param appIds List of app IDs to fetch
     * @param countryCode Country code for pricing (e.g., "US", "GB")
     * @return JSON string ready to be URL-encoded
     */
    private String buildJsonRequest(List<Integer> appIds, String countryCode) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

            // Build the JSON structure: {"ids":[{"appid":123},...], "context":{...}, "data_request":{...}}
            Map<String, Object> request = new HashMap<>();

            // Add app IDs
            List<Map<String, Integer>> ids = appIds.stream()
                    .map(appId -> Map.of("appid", appId))
                    .collect(Collectors.toList());
            request.put("ids", ids);

            // Add context
            Map<String, String> context = new HashMap<>();
            context.put("language", "english");
            context.put("country_code", countryCode != null ? countryCode : "US");
            request.put("context", context);

            // Add data request
            Map<String, Boolean> dataRequest = new HashMap<>();
            dataRequest.put("include_basic_info", true);
            dataRequest.put("include_release", true);
            request.put("data_request", dataRequest);

            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            System.err.println("Failed to build JSON request: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * Fetch price for a single game.
     * Note: For multiple games, use batchGetPrices() instead for better performance.
     */
    public BigDecimal getSinglePrice(Integer appId, String countryCode) {
        Map<Integer, BigDecimal> prices = batchGetPrices(List.of(appId), countryCode);
        return prices.getOrDefault(appId, null);
    }
}
