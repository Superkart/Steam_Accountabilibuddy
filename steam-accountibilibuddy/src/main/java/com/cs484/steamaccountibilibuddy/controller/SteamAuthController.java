package com.cs484.steamaccountibilibuddy.controller;

import com.cs484.steamaccountibilibuddy.dto.SteamOpenidLoginDTO;
import com.cs484.steamaccountibilibuddy.dto.SteamProfileDto;
import com.cs484.steamaccountibilibuddy.security.SteamAuthenticationToken;
import com.cs484.steamaccountibilibuddy.service.SteamService;
import com.cs484.steamaccountibilibuddy.service.UserService;
import com.cs484.steamaccountibilibuddy.util.OpenIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/steam")
public class SteamAuthController {

    @Value("${steam.openid.endpoint:https://steamcommunity.com/openid/login}")
    private String steamOpenIdEndpoint;

    // Provide a sensible default so the app starts when the property is missing
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private final WebClient webClient;
    private final SteamService steamService;
    private final UserService userService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public SteamAuthController(WebClient webClient, SteamService steamService, UserService userService) {
        this.webClient = webClient;
        this.steamService = steamService;
        this.userService = userService;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String returnTo = baseUrl + "/auth/steam/return";

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

        // Return profile info - client should call /library and /wishlist separately to avoid rate limiting
        return ResponseEntity.ok(Map.of(
                "steamId", steamId,
                "username", username != null ? username : "",
                "profilePictureUrl", profilePictureUrl != null ? profilePictureUrl : "",
                "authenticated", true,
                "message", "Authentication successful. Call /auth/steam/library and /auth/steam/wishlist separately."
        ));
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
     * Logout endpoint - clears the session and security context
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logged out successfully"
        ));
    }
}
