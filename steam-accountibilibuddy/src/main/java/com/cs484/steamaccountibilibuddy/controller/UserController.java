package com.cs484.steamaccountibilibuddy.controller;

import com.cs484.steamaccountibilibuddy.entity.User;
import com.cs484.steamaccountibilibuddy.security.SecurityUtils;
import com.cs484.steamaccountibilibuddy.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}
