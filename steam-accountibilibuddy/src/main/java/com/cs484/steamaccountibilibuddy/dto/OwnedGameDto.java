package com.cs484.steamaccountibilibuddy.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OwnedGameDto {
    private Integer appId;
    private String name;
    private Double playtimeHours;
    private String imgSmallUrl;
}
