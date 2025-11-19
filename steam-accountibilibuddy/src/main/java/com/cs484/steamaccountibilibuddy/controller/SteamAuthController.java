package com.cs484.steamaccountibilibuddy.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.cs484.steamaccountibilibuddy.dto.PriceInfo;
import com.cs484.steamaccountibilibuddy.dto.SteamOpenidLoginDTO;
import com.cs484.steamaccountibilibuddy.dto.SteamProfileDto;
import com.cs484.steamaccountibilibuddy.security.SteamAuthenticationToken;
import com.cs484.steamaccountibilibuddy.service.SteamService;
import com.cs484.steamaccountibilibuddy.service.UserService;
import com.cs484.steamaccountibilibuddy.util.OpenIdUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth/steam")
public class SteamAuthController {

    @Value("${steam.openid.endpoint:https://steamcommunity.com/openid/login}")
    private String steamOpenIdEndpoint;

    // Provide a sensible default so the app starts when the property is missing
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private final WebClient webClient;
    private final SteamService steamService;
    private final UserService userService;
    private final com.cs484.steamaccountibilibuddy.service.SteamBatchService steamBatchService;
    private final com.cs484.steamaccountibilibuddy.service.GameService gameService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public SteamAuthController(WebClient webClient, SteamService steamService, UserService userService,
                               com.cs484.steamaccountibilibuddy.service.SteamBatchService steamBatchService,
                               com.cs484.steamaccountibilibuddy.service.GameService gameService) {
        this.webClient = webClient;
        this.steamService = steamService;
        this.userService = userService;
        this.steamBatchService = steamBatchService;
        this.gameService = gameService;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String returnTo = baseUrl + "/api/auth/steam/return";

        String redirect = UriComponentsBuilder
                .fromUriString(steamOpenIdEndpoint)
                .queryParam("openid.ns", "http://specs.openid.net/auth/2.0")
                .queryParam("openid.mode", "checkid_setup")
                .queryParam("openid.return_to", returnTo)
                .queryParam("openid.realm", baseUrl)
                .queryParam("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select")
                .queryParam("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(redirect));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * OpenID return endpoint - handles authentication after Steam login.
     * Returns only authentication status. Client should make separate calls to /library and /wishlist.
     */
    @GetMapping("/return")
    public ResponseEntity<?> openidReturn(@ModelAttribute SteamOpenidLoginDTO dto,
                                          @RequestParam Map<String, String> requestParams,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {

        MultiValueMap<String, String> form = OpenIdUtils.buildVerificationForm(requestParams);

        String resp = webClient.post()
                .uri(steamOpenIdEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (resp == null || !resp.contains("is_valid:true")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "OpenID verification failed"));
        }

        String claimed = requestParams.get("openid.claimed_id");
        String steamId = OpenIdUtils.extractSteamIdFromClaimedId(claimed);
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "steamId missing"));
        }

        // Fetch user profile from Steam API (username and profile picture)
        SteamProfileDto profile = steamService.getPlayerProfile(steamId);
        String username = profile != null ? profile.getUsername() : null;
        String profilePictureUrl = profile != null ? profile.getProfilePictureUrl() : null;

        // Create or update user in database with username
        if (username != null) {
            userService.createOrUpdateUser(steamId, username);
        } else {
            userService.createOrUpdateUser(steamId);
        }

        // Set authentication in security context
        SteamAuthenticationToken authentication = new SteamAuthenticationToken(
                steamId,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Explicitly save the security context to the session
        securityContextRepository.saveContext(securityContext, request, response);

        // Redirect back to frontend after successful authentication
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(frontendUrl + "/?auth=success"));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * Get authenticated user's Steam library with game details and tags.
     * This endpoint should be called separately after authentication to avoid rate limiting.
     */
    @GetMapping("/library")
    public ResponseEntity<?> getLibrary() {
        String steamId = com.cs484.steamaccountibilibuddy.security.SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            return ResponseEntity.ok(steamService.getLibrary(steamId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get authenticated user's Steam wishlist with game details and tags.
     * This endpoint should be called separately after authentication to avoid rate limiting.
     */
    @GetMapping("/wishlist")
    public ResponseEntity<?> getWishlist() {
        String steamId = com.cs484.steamaccountibilibuddy.security.SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            return ResponseEntity.ok(steamService.getWishlist(steamId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get current authenticated user's information.
     * This endpoint returns the user's profile data including Steam username and profile picture.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        String steamId = com.cs484.steamaccountibilibuddy.security.SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            // Fetch user profile from Steam API
            SteamProfileDto profile = steamService.getPlayerProfile(steamId);

            // Get username from database (fallback to Steam if not stored)
            String username = userService.getUserBySteamId(steamId)
                    .map(com.cs484.steamaccountibilibuddy.entity.User::getUsername)
                    .orElse(profile != null ? profile.getUsername() : null);

            return ResponseEntity.ok(Map.of(
                    "steamId", steamId,
                    "username", username != null ? username : "",
                    "profilePictureUrl", profile != null && profile.getProfilePictureUrl() != null ? profile.getProfilePictureUrl() : "",
                    "authenticated", true
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch user profile: " + e.getMessage()));
        }
    }

    /**
     * Logout endpoint - clears the session and security context
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        // Clear the security context
        SecurityContextHolder.clearContext();

        // Invalidate the HTTP session
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logged out successfully"
        ));
    }

    /**
     * Clear the game details cache.
     * This will force all games to be re-fetched with updated information (e.g., community tags).
     * Use this when you want to refresh cached game data.
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<?> clearCache() {
        String steamId = com.cs484.steamaccountibilibuddy.security.SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            gameService.clearCache();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Game cache cleared successfully. Your library and wishlist will now fetch fresh data with community tags."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to clear cache: " + e.getMessage()));
        }
    }

    /**
     * EXPERIMENTAL: Test endpoint for batch price fetching using real wishlist data.
     * Fetches your wishlist and tests the batch API with those app IDs.
     * Test with: GET /auth/steam/test-batch (requires authentication)
     */
    @GetMapping("/test-batch")
    public ResponseEntity<?> testBatchApi() {
        String steamId = com.cs484.steamaccountibilibuddy.security.SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated. Please login via /auth/steam/login first."));
        }

        try {
            // Fetch wishlist to get real app IDs
            System.out.println("Fetching wishlist for steamId: " + steamId);
            List<com.cs484.steamaccountibilibuddy.dto.WishlistEntryDto> wishlist =
                    steamService.getWishlist(steamId);

            if (wishlist.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "message", "Your wishlist is empty. Add some games to test the batch API.",
                    "wishlistSize", 0
                ));
            }

            // Extract app IDs (limit to first 10 for testing)
            List<Integer> appIdList = wishlist.stream()
                    .limit(10)
                    .map(com.cs484.steamaccountibilibuddy.dto.WishlistEntryDto::getAppId)
                    .filter(id -> id != null)
                    .toList();

            System.out.println("Testing batch API with " + appIdList.size() + " games from your wishlist");

            // Use the batch API to fetch prices
            java.util.Map<Integer, PriceInfo> prices = steamBatchService.batchGetPrices(appIdList, "US");

            return ResponseEntity.ok(Map.of(
                    "message", "Batch API test complete! Check console for details.",
                    "wishlistSize", wishlist.size(),
                    "testedAppIds", appIdList,
                    "pricesFetched", prices.size(),
                    "prices", prices
            ));
        } catch (com.cs484.steamaccountibilibuddy.exception.PrivateProfileException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Wishlist is private: " + e.getMessage()));
        } catch (Exception e) {
            System.err.println("Error testing batch API: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
