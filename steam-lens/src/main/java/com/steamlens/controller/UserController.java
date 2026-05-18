package com.steamlens.controller;

import com.steamlens.entity.PriceAlert;
import com.steamlens.entity.User;
import com.steamlens.security.SecurityUtils;
import com.steamlens.service.PriceAlertService;
import com.steamlens.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    private final PriceAlertService priceAlertService;

    public UserController(UserService userService, PriceAlertService priceAlertService) {
        this.userService = userService;
        this.priceAlertService = priceAlertService;
    }

    /**
     * Save or update the user's email address.
     * Requires authentication.
     */
    @PostMapping("/email")
    public ResponseEntity<?> saveEmail(@RequestBody Map<String, String> body) {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email is required"));
        }

        // Basic email validation
        if (!email.contains("@") || !email.contains(".")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid email format"));
        }

        User user = userService.updateUserEmail(steamId, email);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Email saved successfully",
                "email", user.getEmail()
        ));
    }

    /**
     * Get the current user's profile information.
     * Requires authentication.
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        return userService.getUserBySteamId(steamId)
                .map(user -> ResponseEntity.ok(Map.of(
                        "steamId", user.getSteamId(),
                        "email", user.getEmail() != null ? user.getEmail() : "",
                        "createdAt", user.getCreatedAt().toString(),
                        "updatedAt", user.getUpdatedAt().toString()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found")));
    }

    /**
     * Delete the user's email address and all associated price alerts.
     * Since price alerts require an email for notifications, they are automatically removed.
     * Requires authentication.
     */
    @DeleteMapping("/email")
    public ResponseEntity<?> deleteEmail() {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            // Get count of alerts before deletion
            List<PriceAlert> alerts = priceAlertService.getAllAlertsForUser(steamId);
            int alertCount = alerts.size();

            // Delete all price alerts first (since they require email)
            for (PriceAlert alert : alerts) {
                priceAlertService.deletePriceAlert(steamId, alert.getAppId());
            }

            // Then delete the email
            userService.updateUserEmail(steamId, null);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Email deleted successfully",
                    "deletedAlerts", alertCount
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete email: " + e.getMessage()));
        }
    }
}
