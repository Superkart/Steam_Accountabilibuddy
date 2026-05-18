package com.steamlens;

import com.steamlens.util.OpenIdUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SteamLensApplicationTests {

    @Test
    void steamIdExtractedCorrectly() {
        String claimed = "https://steamcommunity.com/openid/id/76561198012345678";
        assertEquals("76561198012345678", OpenIdUtils.extractSteamIdFromClaimedId(claimed));
    }
}
