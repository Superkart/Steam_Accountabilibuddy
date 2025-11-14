package com.cs484.steamaccountibilibuddy.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.cs484.steamaccountibilibuddy.dto.GameDetailsDto;
import com.cs484.steamaccountibilibuddy.dto.OwnedGameDto;
import com.cs484.steamaccountibilibuddy.dto.SteamProfileDto;
import com.cs484.steamaccountibilibuddy.dto.WishlistEntryDto;
import com.cs484.steamaccountibilibuddy.exception.PrivateProfileException;

@Service
public class SteamService {
    private final WebClient webClient;
    private final GameService gameService;
    private final SteamBatchService steamBatchService;

    @Value("${steam.api.key}")
    private String apiKey;

    @Value("${steam.api.base}")
    private String apiBase;


    public SteamService(WebClient webClient, GameService gameService, SteamBatchService steamBatchService) {
        this.webClient = webClient;
        this.gameService = gameService;
        this.steamBatchService = steamBatchService;
    }

    @SuppressWarnings("unchecked")
    public List<OwnedGameDto> getLibrary(String steamId) {
        // Exclude free games by setting include_played_free_games=0
        String uri = apiBase + "/IPlayerService/GetOwnedGames/v1/?key={key}&steamid={steamId}&include_appinfo=1&include_played_free_games=0";

        try {
            Map<String, Object> resp = webClient.get()
                    .uri(uri, apiKey, steamId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return Collections.emptyList();

            Map<String, Object> response = (Map<String, Object>) resp.get("response");
            if (response == null) return Collections.emptyList();

            // Check if 'games' key exists - if missing entirely (not just empty), profile might be private
            if (!response.containsKey("games") && !response.containsKey("game_count")) {
                throw new PrivateProfileException("library");
            }

            List<Map<String, Object>> games = (List<Map<String, Object>>) response.getOrDefault("games", Collections.emptyList());

            // Additional check: if game_count > 0 but games is empty/null, profile is likely private
            Number gameCount = (Number) response.get("game_count");
            if (gameCount != null && gameCount.intValue() > 0 && (games == null || games.isEmpty())) {
                throw new PrivateProfileException("library");
            }

            if (games == null) return Collections.emptyList();

            // Filter to only include games with 2 hours or less of playtime
            // This focuses on unplayed/little-played games for recommendations
            return games.stream()
                .filter(g -> {
                    Number playtimeNum = (Number) g.getOrDefault("playtime_forever", 0);
                    int playtimeMinutes = playtimeNum.intValue();
                    double playtimeHours = playtimeMinutes / 60.0;
                    return playtimeHours <= 2.0;
                })
                .map(g -> {
                Number appidNum = (Number) g.get("appid");
                Integer appid = appidNum != null ? appidNum.intValue() : null;
                String name = (String) g.get("name");
                Number playtimeNum = (Number) g.getOrDefault("playtime_forever", 0);
                int playtimeMinutes = playtimeNum.intValue();
                String imgIcon = (String) g.getOrDefault("img_icon_url", "");
                String imgSmall = appid != null ? "https://media.steampowered.com/steamcommunity/public/images/apps/" + appid + "/" + imgIcon + ".jpg" : null;

                // Fetch game details (tags) from cache or API
                List<String> tags = Collections.emptyList();
                if (appid != null) {
                    GameDetailsDto details = fetchGameDetails(appid);
                    if (details != null && details.getTags() != null) {
                        tags = details.getTags();
                    }
                }

                OwnedGameDto dto = new OwnedGameDto();
                dto.setAppId(appid);
                dto.setName(name);
                dto.setPlaytimeHours(Math.round((playtimeMinutes / 60.0) * 10.0) / 10.0);
                dto.setImgSmallUrl(imgSmall);
                dto.setTags(tags);
                return dto;
            }).collect(Collectors.toList());
        } catch (WebClientResponseException e) {
            // Handle HTTP errors (403 Forbidden, 401 Unauthorized, etc.)
            if (e.getStatusCode().is4xxClientError()) {
                throw new PrivateProfileException("library");
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public List<WishlistEntryDto> getWishlist(String steamId) {
        if (steamId == null || steamId.isBlank()) return Collections.emptyList();

        String uri = apiBase + "/IWishlistService/GetWishlist/v1/?steamid={steamId}";

        try {
            Map<String, Object> resp = webClient.get()
                    .uri(uri, steamId)
                    .header("x-webapi-key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return Collections.emptyList();

            Map<String, Object> response = (Map<String, Object>) resp.get("response");
            if (response == null) return Collections.emptyList();

            Object itemsObj = response.get("items");
            if (!(itemsObj instanceof List)) {
                // If items is null or not a list, profile might be private
                throw new PrivateProfileException("wishlist");
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;

            // First pass: Build DTOs with game details from cache/API
            List<WishlistEntryDto> wishlistEntries = items.stream().map(item -> {
                Number appidNum = (Number) item.get("appid");
                Integer appId = appidNum != null ? appidNum.intValue() : null;

                Number priorityNum = (Number) item.getOrDefault("priority", 0);
                Integer priority = priorityNum != null ? priorityNum.intValue() : 0;

                Number dateAddedNum = (Number) item.getOrDefault("date_added", 0);
                long dateAdded = dateAddedNum != null ? dateAddedNum.longValue() : 0L;

                // Fetch game details (name and tags) from cache or Steam Store API
                String name = null;
                List<String> tags = Collections.emptyList();
                if (appId != null) {
                    GameDetailsDto details = fetchGameDetails(appId);
                    if (details != null) {
                        name = details.getName();
                        tags = details.getTags() != null ? details.getTags() : Collections.emptyList();
                    }
                }

                WishlistEntryDto dto = new WishlistEntryDto();
                dto.setAppId(appId);
                dto.setName(name);
                dto.setPriority(String.valueOf(priority));
                dto.setAddedAt(dateAdded);
                dto.setTags(tags);
                return dto;
            }).collect(Collectors.toList());

            // Second pass: Batch fetch prices for all wishlist items
            List<Integer> appIds = wishlistEntries.stream()
                    .map(WishlistEntryDto::getAppId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());

            if (!appIds.isEmpty()) {
                Map<Integer, java.math.BigDecimal> prices = steamBatchService.batchGetPrices(appIds, "US");
                wishlistEntries.forEach(entry -> {
                    if (entry.getAppId() != null) {
                        entry.setCurrentPrice(prices.get(entry.getAppId()));
                    }
                });
            }

            return wishlistEntries;
        } catch (WebClientResponseException e) {
            // Handle HTTP errors (403 Forbidden, 401 Unauthorized, etc.)
            if (e.getStatusCode().is4xxClientError()) {
                throw new PrivateProfileException("wishlist");
            }
            throw e;
        }
    }

    /**
     * Fetches game details (name and tags) from the database cache or Steam Store API.
     * Checks cache first, then fetches from Steam API if not cached, and saves to cache.
     *
     * @param appId The Steam app ID
     * @return GameDetailsDto containing name and tags, or null if the request fails
     */
    @SuppressWarnings("unchecked")
    public GameDetailsDto fetchGameDetails(Integer appId) {
        if (appId == null) return null;

        // Check cache first
        Optional<GameDetailsDto> cachedGame = gameService.getGameFromCache(appId);
        if (cachedGame.isPresent()) {
            GameDetailsDto cached = cachedGame.get();
            // Re-fetch if tags are empty or null (was cached before tags were properly fetched)
            if (cached.getTags() != null && !cached.getTags().isEmpty()) {
                System.out.println("Cache HIT for appId " + appId);
                return cached;
            }
            System.out.println("Cache HIT for appId " + appId + " but tags empty - re-fetching");
        } else {
            System.out.println("Cache MISS for appId " + appId + " - fetching from Steam API");
        }

        // Not in cache, fetch from Steam API
        try {
            String uri = "https://store.steampowered.com/api/appdetails?appids={appId}";

            Map<String, Object> resp = webClient.get()
                    .uri(uri, appId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return null;

            // Response structure: { "12345": { "success": true, "data": { ... } } }
            Map<String, Object> appData = (Map<String, Object>) resp.get(String.valueOf(appId));
            if (appData == null || !Boolean.TRUE.equals(appData.get("success"))) {
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) appData.get("data");
            if (data == null) return null;

            String name = (String) data.get("name");

            // Extract tags from categories/genres
            List<String> tags = new ArrayList<>();

            // Get genres (more general categorization)
            List<Map<String, Object>> genres = (List<Map<String, Object>>) data.get("genres");
            if (genres != null) {
                genres.forEach(genre -> {
                    String description = (String) genre.get("description");
                    if (description != null) tags.add(description);
                });
            }

            // Get categories (features like "Single-player", "Multi-player", etc.)
            List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("categories");
            if (categories != null) {
                categories.forEach(category -> {
                    String description = (String) category.get("description");
                    if (description != null) tags.add(description);
                });
            }

            // Debug logging for games with no tags
            if (tags.isEmpty()) {
                System.out.println("WARNING: AppId " + appId + " (" + name + ") has no genres/categories in Steam API response");
            }

            GameDetailsDto gameDetails = new GameDetailsDto(name, tags);

            // Save to cache for future requests
            gameService.cacheGameDetails(appId, gameDetails);

            // Add delay ONLY when fetching from Steam API to prevent rate limiting
            try {
                Thread.sleep(300); // 300ms delay between Steam API requests
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return gameDetails;
        } catch (Exception e) {
            // Log error and return null if fetching fails
            System.err.println("Error fetching game details for appId " + appId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetches user profile information from Steam API.
     * Uses the ISteamUser/GetPlayerSummaries endpoint.
     *
     * @param steamId The Steam ID
     * @return SteamProfileDto containing username and profile picture URL, or null if the request fails
     */
    @SuppressWarnings("unchecked")
    public SteamProfileDto getPlayerProfile(String steamId) {
        if (steamId == null || steamId.isBlank()) return null;

        try {
            String uri = apiBase + "/ISteamUser/GetPlayerSummaries/v2/?key={key}&steamids={steamId}";

            Map<String, Object> resp = webClient.get()
                    .uri(uri, apiKey, steamId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return null;

            Map<String, Object> response = (Map<String, Object>) resp.get("response");
            if (response == null) return null;

            List<Map<String, Object>> players = (List<Map<String, Object>>) response.get("players");
            if (players == null || players.isEmpty()) return null;

            Map<String, Object> player = players.get(0);

            String username = (String) player.get("personaname");
            String avatarFull = (String) player.get("avatarfull"); // 184x184 profile picture

            return new SteamProfileDto(steamId, username, avatarFull);
        } catch (Exception e) {
            System.err.println("Error fetching player profile for steamId " + steamId + ": " + e.getMessage());
            return null;
        }
    }
}
