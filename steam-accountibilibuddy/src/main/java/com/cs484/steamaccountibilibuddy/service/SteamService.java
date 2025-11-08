package com.cs484.steamaccountibilibuddy.service;

import com.cs484.steamaccountibilibuddy.dto.OwnedGameDto;
import com.cs484.steamaccountibilibuddy.dto.WishlistEntryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SteamService {
    // at top of SteamService
    private final WebClient webClient;

    @Value("${steam.api.key}")
    private String apiKey;

    @Value("${steam.api.base}")
    private String apiBase;


    public SteamService(WebClient webClient) {
        this.webClient = webClient;
    }

    @SuppressWarnings("unchecked")
    public List<OwnedGameDto> getLibrary(String steamId) {
        String uri = apiBase + "/IPlayerService/GetOwnedGames/v1/?key={key}&steamid={steamId}&include_appinfo=1&include_played_free_games=1";

        Map<String, Object> resp = webClient.get()
                .uri(uri, apiKey, steamId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null) return Collections.emptyList();

        Map<String, Object> response = (Map<String, Object>) resp.get("response");
        if (response == null) return Collections.emptyList();

        List<Map<String, Object>> games = (List<Map<String, Object>>) response.getOrDefault("games", Collections.emptyList());
        return games.stream().map(g -> {
            Number appidNum = (Number) g.get("appid");
            Integer appid = appidNum != null ? appidNum.intValue() : null;
            String name = (String) g.get("name");
            Number playtimeNum = (Number) g.getOrDefault("playtime_forever", 0);
            int playtimeMinutes = playtimeNum.intValue();
            String imgIcon = (String) g.getOrDefault("img_icon_url", "");
            String imgSmall = appid != null ? "https://media.steampowered.com/steamcommunity/public/images/apps/" + appid + "/" + imgIcon + ".jpg" : null;

            OwnedGameDto dto = new OwnedGameDto();
            dto.setAppId(appid);
            dto.setName(name);
            dto.setPlaytimeHours(Math.round((playtimeMinutes / 60.0) * 10.0) / 10.0);
            dto.setImgSmallUrl(imgSmall);
            return dto;
        }).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public List<WishlistEntryDto> getWishlist(String steamId) {
        if (steamId == null || steamId.isBlank()) return Collections.emptyList();

        String uri = apiBase + "/IWishlistService/GetWishlist/v1/?steamid={steamId}";

        Map<String, Object> resp = webClient.get()
                .uri(uri, steamId)
                .header("x-webapi-key", apiKey) // include if required by your key
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null) return Collections.emptyList();

        Map<String, Object> response = (Map<String, Object>) resp.get("response");
        if (response == null) return Collections.emptyList();

        Object itemsObj = response.get("items");
        if (!(itemsObj instanceof List)) return Collections.emptyList();

        List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;

        return items.stream().map(item -> {
            Number appidNum = (Number) item.get("appid");
            Integer appId = appidNum != null ? appidNum.intValue() : null;

            Number priorityNum = (Number) item.getOrDefault("priority", 0);
            Integer priority = priorityNum != null ? priorityNum.intValue() : 0;

            Number dateAddedNum = (Number) item.getOrDefault("date_added", 0);
            long dateAdded = dateAddedNum != null ? dateAddedNum.longValue() : 0L;

            WishlistEntryDto dto = new WishlistEntryDto();
            dto.setAppId(appId);
            dto.setName(null); // IWishlistService/items only include appid/priority/date_added; name requires separate lookup
            dto.setPriority(String.valueOf(priority));
            dto.setAddedAt(dateAdded);
            return dto;
        }).collect(Collectors.toList());
    }
}
