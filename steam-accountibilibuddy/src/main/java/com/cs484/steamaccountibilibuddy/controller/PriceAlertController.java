package com.cs484.steamaccountibilibuddy.controller;

import com.cs484.steamaccountibilibuddy.entity.PriceAlert;
import com.cs484.steamaccountibilibuddy.security.SecurityUtils;
import com.cs484.steamaccountibilibuddy.service.PriceAlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/price-alerts")
public class PriceAlertController {
    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    /**
     * Create or update a price alert for a game.
     * Requires authentication and user must have email set.
     */
    @PostMapping
    public ResponseEntity<?> createOrUpdateAlert(@RequestBody Map<String, Object> body) {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            Integer appId = (Integer) body.get("appId");
            String gameName = (String) body.get("gameName");
            Object targetPriceObj = body.get("targetPrice");

            if (appId == null || gameName == null || targetPriceObj == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "appId, gameName, and targetPrice are required"));
            }

            BigDecimal targetPrice = new BigDecimal(targetPriceObj.toString());

            PriceAlert alert = priceAlertService.createOrUpdatePriceAlert(steamId, appId, gameName, targetPrice);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Price alert created/updated successfully",
                    "alert", convertToMap(alert)
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create price alert: " + e.getMessage()));
        }
    }

    /**
     * Get all price alerts for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<?> getAllAlerts() {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        List<PriceAlert> alerts = priceAlertService.getAllAlertsForUser(steamId);

        List<Map<String, Object>> alertMaps = alerts.stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("alerts", alertMaps));
    }

    /**
     * Delete a price alert by app ID.
     */
    @DeleteMapping("/{appId}")
    public ResponseEntity<?> deleteAlert(@PathVariable Integer appId) {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            priceAlertService.deletePriceAlert(steamId, appId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Price alert deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete price alert: " + e.getMessage()));
        }
    }

    /**
     * Helper method to convert PriceAlert entity to a map for JSON response.
     */
    private Map<String, Object> convertToMap(PriceAlert alert) {
        return Map.of(
                "appId", alert.getAppId(),
                "gameName", alert.getGameName() != null ? alert.getGameName() : "",
                "targetPrice", alert.getTargetPrice().toString(),
                "currentPrice", alert.getCurrentPrice() != null ? alert.getCurrentPrice().toString() : null,
                "lastChecked", alert.getLastChecked() != null ? alert.getLastChecked().toString() : null,
                "createdAt", alert.getCreatedAt().toString()
        );
    }
}
