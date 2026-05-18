package com.steamlens.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenIdUtilsTest {

    @Test
    void extractsSteamIdFromValidClaimedId() {
        String claimedId = "https://steamcommunity.com/openid/id/76561198000000000";
        assertEquals("76561198000000000", OpenIdUtils.extractSteamIdFromClaimedId(claimedId));
    }

    @Test
    void returnsNullForNullInput() {
        assertNull(OpenIdUtils.extractSteamIdFromClaimedId(null));
    }

    @Test
    void returnsNullWhenNoSlashFound() {
        assertNull(OpenIdUtils.extractSteamIdFromClaimedId("noSlashHere"));
    }

    @Test
    void buildVerificationFormSetsCheckAuthenticationMode() {
        var params = new java.util.HashMap<String, String>();
        params.put("openid.mode", "id_res");
        params.put("openid.ns", "http://specs.openid.net/auth/2.0");

        var form = OpenIdUtils.buildVerificationForm(params);

        assertEquals("check_authentication", form.getFirst("openid.mode"));
        assertEquals("http://specs.openid.net/auth/2.0", form.getFirst("openid.ns"));
    }

    @Test
    void buildVerificationFormHandlesNullParams() {
        var form = OpenIdUtils.buildVerificationForm(null);
        assertEquals("check_authentication", form.getFirst("openid.mode"));
    }
}
