package com.steamlens.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    /**
     * Get the Steam store URL for a game by app ID.
     * This endpoint returns the Steam store page URL that can be opened in a new tab.
     */
    @GetMapping("/buy-now/{appId}")
    public ResponseEntity<?> getBuyNowUrl(@PathVariable Integer appId) {
        if (appId == null || appId <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid app ID"));
        }

        String steamStoreUrl = "https://store.steampowered.com/app/" + appId + "/";
        return ResponseEntity.ok(Map.of("storeUrl", steamStoreUrl));
    }
}
