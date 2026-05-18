package com.steamlens.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class OwnedGameDto {
    private Integer appId;
    private String name;
    private Double playtimeHours;
    private String imgSmallUrl;
    private List<String> tags;
}
