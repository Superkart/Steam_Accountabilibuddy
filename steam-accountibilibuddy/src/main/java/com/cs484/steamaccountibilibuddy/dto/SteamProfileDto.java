package com.cs484.steamaccountibilibuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SteamProfileDto {
    private String steamId;
    private String username;
    private String profilePictureUrl;
}
