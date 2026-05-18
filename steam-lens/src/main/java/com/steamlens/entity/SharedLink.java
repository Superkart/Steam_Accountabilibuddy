package com.steamlens.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "shared_links")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "steam_id", nullable = false)
    private String steamId;

    @Column(name = "type", nullable = false)
    private String type; // e.g. "wishlist"

    @Column(name = "snapshot_data", columnDefinition = "TEXT", nullable = false)
    private String snapshotData; // JSON snapshot of the shared data

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
