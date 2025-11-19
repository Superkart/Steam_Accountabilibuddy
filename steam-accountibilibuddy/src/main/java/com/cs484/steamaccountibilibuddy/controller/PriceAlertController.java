package com.cs484.steamaccountibilibuddy.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cs484.steamaccountibilibuddy.entity.PriceAlert;
import com.cs484.steamaccountibilibuddy.scheduler.PriceCheckScheduler;
import com.cs484.steamaccountibilibuddy.security.SecurityUtils;
import com.cs484.steamaccountibilibuddy.service.PriceAlertService;

@RestController
@RequestMapping("/api/price-alerts")
public class PriceAlertController {
    private final PriceAlertService priceAlertService;
    private final PriceCheckScheduler priceCheckScheduler;

    /*
     * Tracks a background job's status and creation time for cleanup.
     */
    private static class JobStatus {
        private final String status;
        private final java.time.Instant createdAt;

        public JobStatus(String status) {
            this.status = status;
            this.createdAt = java.time.Instant.now();
        }

        public String getStatus() {
            return status;
        }

        public boolean isOlderThan(java.time.Duration duration) {
            return createdAt.plus(duration).isBefore(java.time.Instant.now());
        }
    }

    // In-memory map to track background job statuses. Key = jobId, Value = status string.
    private final ConcurrentHashMap<String, JobStatus> backgroundJobStatuses = new ConcurrentHashMap<>();

    public PriceAlertController(PriceAlertService priceAlertService, PriceCheckScheduler priceCheckScheduler) {
        this.priceAlertService = priceAlertService;
        this.priceCheckScheduler = priceCheckScheduler;
    }

    /**
     * Create or update a price alert for a game.
     * Requires authentication and user must have email set.
     */
    @PostMapping
    public ResponseEntity<?> createOrUpdateAlert(@RequestBody Map<String, Object> body) {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            Integer appId = (Integer) body.get("appId");
            String gameName = (String) body.get("gameName");
            Object targetPriceObj = body.get("targetPrice");

            if (appId == null || gameName == null || targetPriceObj == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "appId, gameName, and targetPrice are required"));
            }

            BigDecimal targetPrice = new BigDecimal(targetPriceObj.toString());

            PriceAlert alert = priceAlertService.createOrUpdatePriceAlert(steamId, appId, gameName, targetPrice);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Price alert created/updated successfully",
                    "alert", convertToMap(alert)
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create price alert: " + e.getMessage()));
        }
    }

    /**
     * Get all price alerts for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<?> getAllAlerts() {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        List<PriceAlert> alerts = priceAlertService.getAllAlertsForUser(steamId);

        List<Map<String, Object>> alertMaps = alerts.stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("alerts", alertMaps));
    }

    /**
     * Delete a price alert by app ID.
     */
    @DeleteMapping("/{appId}")
    public ResponseEntity<?> deleteAlert(@PathVariable Integer appId) {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            priceAlertService.deletePriceAlert(steamId, appId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Price alert deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete price alert: " + e.getMessage()));
        }
    }

    /**
     * Delete all price alerts for the authenticated user.
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAllAlerts() {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            List<PriceAlert> alerts = priceAlertService.getAllAlertsForUser(steamId);
            int count = alerts.size();

            for (PriceAlert alert : alerts) {
                priceAlertService.deletePriceAlert(steamId, alert.getAppId());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "All price alerts deleted successfully",
                    "deletedCount", count
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete price alerts: " + e.getMessage()));
        }
    }

    /**
     * Manually trigger price check for all alerts (for testing).
     * Requires authentication.
     */
    @PostMapping("/check-now")
    public ResponseEntity<?> checkPricesNow() {
        String steamId = SecurityUtils.getCurrentSteamId();
        if (steamId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        // Run the manual price check asynchronously to avoid blocking the HTTP thread.
        String jobId = UUID.randomUUID().toString();
        backgroundJobStatuses.put(jobId, new JobStatus("queued"));

        CompletableFuture.runAsync(() -> {
            backgroundJobStatuses.put(jobId, new JobStatus("running"));
            try {
                priceCheckScheduler.manualPriceCheck();
                backgroundJobStatuses.put(jobId, new JobStatus("completed"));
            } catch (Exception e) {
                backgroundJobStatuses.put(jobId, new JobStatus("failed: " + e.getMessage()));
            }
        })
        .orTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
        .exceptionally(ex -> {
            backgroundJobStatuses.put(jobId, new JobStatus("failed: Operation time out after 5 minutes"));
            System.err.println("Price check job " + jobId + "time out: " + ex.getMessage());
            return null;
        });

        return ResponseEntity.ok(Map.of(
                "success", true,
                "jobId", jobId,
                "message", "Checking prices now... If any games are below your target price, you'll receive an email."
        ));
    }

    /**
     * Get status for an asynchronously started price-check job.
     */
    @GetMapping("/check-status/{jobId}")
    public ResponseEntity<?> getCheckStatus(@PathVariable String jobId) {
        JobStatus jobStatus = backgroundJobStatuses.get(jobId);
        if (jobStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found or expired"));
        }
        return ResponseEntity.ok(Map.of("jobId", jobId, "status", jobStatus.getStatus()));
    }

    /**
     * Helper method to convert PriceAlert entity to a map for JSON response.
     */
    private Map<String, Object> convertToMap(PriceAlert alert) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("appId", alert.getAppId());
        map.put("gameName", alert.getGameName() != null ? alert.getGameName() : "");
        map.put("targetPrice", alert.getTargetPrice().toString());
        map.put("currentPrice", alert.getCurrentPrice() != null ? alert.getCurrentPrice().toString() : null);
        map.put("lastChecked", alert.getLastChecked() != null ? alert.getLastChecked().toString() : null);
        map.put("createdAt", alert.getCreatedAt().toString());
        return map;
    }

    /**
     * Cleanup job that runs every hour to remove old job statuses.
     * Prevents memory leaks from completed jobs that are never queried again.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupOldJobStatuses() {
        System.out.println("Running job cleanup... Current jobs in memory: " + backgroundJobStatuses.size());

        java.time.Duration maxAge = java.time.Duration.ofHours(1); // Keep job statuses for 1 hour
        int sizeBefore = backgroundJobStatuses.size();

        backgroundJobStatuses.entrySet().removeIf(entry -> {
            return entry.getValue().isOlderThan(maxAge);
        });

        int removed = sizeBefore - backgroundJobStatuses.size();
        if (removed > 0) {
            System.out.println("✓ Cleaned up " + removed + " old job statuses from memory. Remaining: " + backgroundJobStatuses.size());
        } else {
            System.out.println("✓ No old job statuses to clean up.");
        }
    }
}
