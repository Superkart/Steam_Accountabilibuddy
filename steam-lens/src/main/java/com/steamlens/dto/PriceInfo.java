package com.steamlens.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceInfo {
    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private Integer discountPercent;  // null if not on sale, otherwise 0-100
}
