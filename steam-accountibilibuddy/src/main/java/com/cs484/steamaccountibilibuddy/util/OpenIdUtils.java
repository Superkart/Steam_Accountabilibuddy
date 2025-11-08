package com.cs484.steamaccountibilibuddy.util;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.Objects;

public class OpenIdUtils {

    /**
     * Extract the numeric SteamID from the claimed_id Steam returns:
     * example claimed_id: https://steamcommunity.com/openid/id/7656119...
     */
    public static String extractSteamIdFromClaimedId(String claimedId) {
        if (claimedId == null) return null;
        int idx = claimedId.lastIndexOf('/');
        if (idx < 0 || idx == claimedId.length() - 1) return null;
        return claimedId.substring(idx + 1);
    }

    /**
     * Build a MultiValueMap form containing all received request params
     * (so every openid.* param Steam sent is forwarded), then set
     * openid.mode to check_authentication per the OpenID 2.0 spec.
     *
     * Use this MultiValueMap as the body for a POST with content-type application/x-www-form-urlencoded.
     */
    public static MultiValueMap<String, String> buildVerificationForm(Map<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (params == null || params.isEmpty()) {
            form.set("openid.mode", "check_authentication");
            return form;
        }

        params.forEach((k, v) -> {
            if (k == null || v == null) return;
            // Spring's form map expects the exact parameter names Steam returned, e.g. "openid.ns"
            form.add(k, v);
        });

        // Ensure verification mode is set per spec (override any incoming mode)
        form.set("openid.mode", "check_authentication");
        return form;
    }
}
