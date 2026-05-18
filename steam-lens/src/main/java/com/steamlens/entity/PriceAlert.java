package com.steamlens.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_alerts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"steam_id", "app_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "steam_id", nullable = false)
    private String steamId;

    @Column(name = "app_id", nullable = false)
    private Integer appId;

    @Column(name = "game_name")
    private String gameName;

    @Column(name = "target_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "current_price", precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "last_checked")
    private LocalDateTime lastChecked;

    @Column(name = "last_notification_sent")
    private LocalDateTime lastNotificationSent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steam_id", insertable = false, updatable = false)
    private User user;
}
