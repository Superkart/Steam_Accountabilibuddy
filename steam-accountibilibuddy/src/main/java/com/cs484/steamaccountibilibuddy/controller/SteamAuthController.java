package com.cs484.steamaccountibilibuddy.controller;

import com.cs484.steamaccountibilibuddy.dto.SteamOpenidLoginDTO;
import com.cs484.steamaccountibilibuddy.service.SteamService;
import com.cs484.steamaccountibilibuddy.util.OpenIdUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
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

    public SteamAuthController(WebClient webClient, SteamService steamService) {
        this.webClient = webClient;
        this.steamService = steamService;
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

    /*
    //This just returns the user then make calls to /library and /wishlist to get that data
    @GetMapping("/return")
    public ResponseEntity<?> openidReturn(@ModelAttribute SteamOpenidLoginDTO dto,
                                          @RequestParam Map<String, String> requestParams) {

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

        // For demo: return steamId. In production issue JWT / session.
        return ResponseEntity.ok(Map.of("steamId", steamId));
    }
    */

    // This is for if we want to return both the library and wishlist on the return call
    // one problem is the wishlist return doesn't include app name yet since we need to find
    // a way to make a call for each appid in the wishlist to get the name
    @GetMapping("/return")
    public ResponseEntity<?> openidReturn(@ModelAttribute SteamOpenidLoginDTO dto,
                                          @RequestParam Map<String, String> requestParams) {

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

        // Fetch library and wishlist synchronously (keep blocking style consistent with existing service)
        List<?> library = Collections.emptyList();
        List<?> wishlist = Collections.emptyList();
        try {
            library = steamService.getLibrary(steamId);
        } catch (Exception e) {
            // log if you have logging; swallow so we can still return what we have
        }
        try {
            wishlist = steamService.getWishlist(steamId);
        } catch (Exception e) {
            // log if you have logging
        }

        Map<String, Object> result = Map.of(
                "steamId", steamId,
                "library", library,
                "wishlist", wishlist
        );

        return ResponseEntity.ok(result);
    }

    // library works as expected, made to use if we don't want auth/steam/return to return the library
    @GetMapping("/library")
    public ResponseEntity<?> getLibrary(@RequestParam String steamId) {
        return ResponseEntity.ok(steamService.getLibrary(steamId));
    }

    // doesn't include app name but everything else works as expected
    @GetMapping("/wishlist")
    public ResponseEntity<?> getWishlist(@RequestParam String steamId) {
        return ResponseEntity.ok(steamService.getWishlist(steamId));
    }
}
