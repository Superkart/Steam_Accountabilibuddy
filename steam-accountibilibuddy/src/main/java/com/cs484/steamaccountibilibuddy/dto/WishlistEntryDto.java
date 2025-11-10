package com.cs484.steamaccountibilibuddy.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class WishlistEntryDto {
    private Integer appId;
    private String name;
    private Long addedAt;
    private String priority;
    private List<String> tags;
}
