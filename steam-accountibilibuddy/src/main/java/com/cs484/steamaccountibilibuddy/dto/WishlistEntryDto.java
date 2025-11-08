package com.cs484.steamaccountibilibuddy.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WishlistEntryDto {
    private Integer appId;
    private String name;
    private Long addedAt;
    private String priority;
}
