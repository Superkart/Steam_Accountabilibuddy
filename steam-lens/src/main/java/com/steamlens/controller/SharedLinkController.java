package com.steamlens.controller;

import com.steamlens.security.SecurityUtils;
import com.steamlens.service.SharedLinkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/share")
public class SharedLinkController {

    private final SharedLinkService sharedLinkService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public SharedLinkController(SharedLinkService sharedLinkService) {
        this.sharedLinkService = sharedLinkService;
    }

    /**
     * Create a share link for the authenticated user's wishlist.
     */
    @PostMapping("/wishlist")
    public ResponseEntity<?> shareWishlist() {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        String uuid = sharedLinkService.createWishlistShare(steamId);
        String shareUrl = frontendUrl.endsWith("/") ? frontendUrl + "share/" + uuid : frontendUrl + "/share/" + uuid;

        return ResponseEntity.ok(Map.of("uuid", uuid, "shareUrl", shareUrl));
    }

    /**
     * Public endpoint to retrieve shared snapshot by uuid.
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<?> getShared(@PathVariable String uuid) {
        return sharedLinkService.getSharedData(uuid)
                .map(data -> ResponseEntity.ok().body(data))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Share link not found")));
    }
}
