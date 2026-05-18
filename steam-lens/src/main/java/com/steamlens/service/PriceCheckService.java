package com.steamlens.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PriceCheckService {
    private final WebClient webClient;

    public PriceCheckService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches the current price of a game from the Steam Store API.
     * Handles both USD and other currencies, converts to USD if needed.
     *
     * @param appId The Steam app ID
     * @return The current price in USD, or null if unavailable/free
     */
    @SuppressWarnings("unchecked")
    public BigDecimal getCurrentPrice(Integer appId) {
        if (appId == null) return null;

        try {
            // Steam Store API endpoint for app details
            // Using cc=us for USD prices
            String uri = "https://store.steampowered.com/api/appdetails?appids={appId}&cc=us&filters=price_overview";

            Map<String, Object> resp = webClient.get()
                    .uri(uri, appId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return null;

            // Response structure: { "12345": { "success": true, "data": { "price_overview": {...} } } }
            Map<String, Object> appData = (Map<String, Object>) resp.get(String.valueOf(appId));
            if (appData == null || !Boolean.TRUE.equals(appData.get("success"))) {
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) appData.get("data");
            if (data == null) return null;

            Map<String, Object> priceOverview = (Map<String, Object>) data.get("price_overview");
            if (priceOverview == null) {
                // Game is free or doesn't have pricing info
                return BigDecimal.ZERO;
            }

            // Get final price (price after discount, in cents)
            Object finalPriceObj = priceOverview.get("final");
            if (finalPriceObj == null) return null;

            // Convert from cents to dollars
            int priceInCents = ((Number) finalPriceObj).intValue();
            return new BigDecimal(priceInCents).divide(new BigDecimal(100));

        } catch (Exception e) {
            // Log error and return null if fetching fails
            System.err.println("Error fetching price for appId " + appId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a game is currently on sale.
     *
     * @param appId The Steam app ID
     * @return true if on sale, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean isOnSale(Integer appId) {
        if (appId == null) return false;

        try {
            String uri = "https://store.steampowered.com/api/appdetails?appids={appId}&filters=price_overview";

            Map<String, Object> resp = webClient.get()
                    .uri(uri, appId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return false;

            Map<String, Object> appData = (Map<String, Object>) resp.get(String.valueOf(appId));
            if (appData == null || !Boolean.TRUE.equals(appData.get("success"))) {
                return false;
            }

            Map<String, Object> data = (Map<String, Object>) appData.get("data");
            if (data == null) return false;

            Map<String, Object> priceOverview = (Map<String, Object>) data.get("price_overview");
            if (priceOverview == null) return false;

            // Check discount_percent field
            Object discountPercent = priceOverview.get("discount_percent");
            return discountPercent != null && ((Number) discountPercent).intValue() > 0;

        } catch (Exception e) {
            System.err.println("Error checking sale status for appId " + appId + ": " + e.getMessage());
            return false;
        }
    }
}
