package com.cs484.steamaccountibilibuddy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "games")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Game {

    @Id
    @Column(name = "app_id", nullable = false)
    private Integer appId;

    @Column(name = "name")
    private String name;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // Stored as comma-separated values

    @Column(name = "img_icon_url")
    private String imgIconUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
