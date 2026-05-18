package com.steamlens.exception;

import lombok.Getter;

@Getter
public class PrivateProfileException extends RuntimeException {
    private final String profileType;
    private final String privacySettingsUrl;

    public PrivateProfileException(String profileType) {
        super(String.format("Your Steam %s is private. Please make it public to use this feature.", profileType));
        this.profileType = profileType;
        this.privacySettingsUrl = "https://steamcommunity.com/my/edit/settings";
    }

    public String getHelpMessage() {
        return String.format("""
                             To change your privacy settings:
                             1. Go to %s
                             2. Under 'Privacy Settings', set 'Game details' to 'Public'
                             3. Click 'Save Changes'
                             4. Try again after updating your settings""",
            privacySettingsUrl
        );
    }
}
