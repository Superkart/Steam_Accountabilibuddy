package com.steamlens.scheduler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.steamlens.dto.PriceInfo;
import com.steamlens.entity.PriceAlert;
import com.steamlens.entity.User;
import com.steamlens.service.EmailService;
import com.steamlens.service.PriceAlertService;
import com.steamlens.service.SteamBatchService;
import com.steamlens.service.UserService;

@Component
public class PriceCheckScheduler {
    private final PriceAlertService priceAlertService;
    private final SteamBatchService steamBatchService;
    private final EmailService emailService;
    private final UserService userService;

    public PriceCheckScheduler(PriceAlertService priceAlertService,
                               SteamBatchService steamBatchService,
                               EmailService emailService,
                               UserService userService) {
        this.priceAlertService = priceAlertService;
        this.steamBatchService = steamBatchService;
        this.emailService = emailService;
        this.userService = userService;
    }

    /**
     * Scheduled job that runs once daily at 9 AM to check all price alerts.
     * Uses batch API for efficient price checking - can handle 500 games per request.
     * Cron expression: "0 0 9 * * ?" means: second=0, minute=0, hour=9, every day
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @org.springframework.transaction.annotation.Transactional
    @SuppressWarnings("BusyWait") // Thread.sleep is intentional for API rate limiting
    public void checkPriceAlerts() {
        System.out.println("Starting daily price check job...");

        List<PriceAlert> allAlerts = priceAlertService.getAllAlerts();
        System.out.println("Found " + allAlerts.size() + " price alerts to check");

        if (allAlerts.isEmpty()) {
            System.out.println("No price alerts to check.");
            return;
        }

        int emailsSent = 0;
        int pricesChecked = 0;

        try {
            // Batch fetch all prices in one go (or in batches of 500 if > 500 alerts)
            List<Integer> appIds = allAlerts.stream()
                    .map(PriceAlert::getAppId)
                    .distinct()
                    .collect(Collectors.toList());

            System.out.println("Fetching prices for " + appIds.size() + " unique games using batch API...");

            // Split into batches of 500 to avoid overwhelming the API
            int batchSize = 500;
            Map<Integer, PriceInfo> allPrices = new java.util.HashMap<>();

            for (int i = 0; i < appIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, appIds.size());
                List<Integer> batch = appIds.subList(i, end);

                System.out.println("Fetching batch " + (i / batchSize + 1) + " (" + batch.size() + " games)...");
                Map<Integer, PriceInfo> batchPrices = steamBatchService.batchGetPrices(batch, "US");
                allPrices.putAll(batchPrices);

                // Small delay between batches to be respectful to Steam's API
                if (end < appIds.size()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Price check interrupted during batch delay: " + e.getMessage());
                        break;
                    }
                }
            }

            System.out.println("Successfully fetched " + allPrices.size() + " prices");

            // Step 1: Update all alerts with current prices
            java.util.List<Integer> skippedIds = new java.util.ArrayList<>();
            for (PriceAlert alert : allAlerts) {
                PriceInfo currentPrice = allPrices.get(alert.getAppId());
                if (currentPrice == null || currentPrice.getCurrentPrice() == null) {
                    // Steam failed to return price info for this appId — skip and record
                    skippedIds.add(alert.getAppId());
                    continue;
                }

                pricesChecked++;
                // Update alert with current price (also resets notification if price went above target)
                priceAlertService.updateAlertPrice(alert, currentPrice.getCurrentPrice());
            }

            if (!skippedIds.isEmpty()) {
                System.out.println("Skipped updating currentPrice for appIds (no data): " + skippedIds);
            }

            // Step 2: Group alerts by user for batched notifications
            Map<String, Map<PriceAlert, BigDecimal>> alertsByUser = new HashMap<>();

            for (PriceAlert alert : allAlerts) {
                PriceInfo currentPrice = allPrices.get(alert.getAppId());

                // Only consider alerts with valid price data
                if (currentPrice != null && currentPrice.getCurrentPrice() != null &&
                    currentPrice.getCurrentPrice().compareTo(alert.getTargetPrice()) < 0 &&
                    alert.getLastNotificationSent() == null) {

                    // Add to user's alert map
                    alertsByUser
                        .computeIfAbsent(alert.getSteamId(), k -> new HashMap<>())
                        .put(alert, currentPrice.getCurrentPrice());
                }
            }

            // Step 3: Send one batched email per user with all their games on sale
            for (Map.Entry<String, Map<PriceAlert, BigDecimal>> userEntry : alertsByUser.entrySet()) {
                String steamId = userEntry.getKey();
                Map<PriceAlert, BigDecimal> userAlerts = userEntry.getValue();

                try {
                    Optional<User> userOpt = userService.getUserBySteamId(steamId);

                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        if (user.getEmail() != null && !user.getEmail().isBlank()) {
                            // Send batched email with all games on sale for this user
                            emailService.sendBatchedPriceDropNotification(user.getEmail(), userAlerts, user.getUsername());

                            // Mark all alerts in this batch as notified
                            for (PriceAlert alert : userAlerts.keySet()) {
                                priceAlertService.markNotificationSent(alert);
                            }

                            emailsSent++;
                            System.out.println("Sent batched notification to " + user.getEmail() + " for " + userAlerts.size() + " games");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error sending batched notification for user " + steamId + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Error during batch price check: " + e.getMessage());
        }

        System.out.println("Price check job completed. Checked " + pricesChecked + " prices, sent " + emailsSent + " batched notifications to users.");
    }

    /**
     * Manual trigger for testing (can be called via a controller if needed).
     */
    @org.springframework.transaction.annotation.Transactional
    public void manualPriceCheck() {
        System.out.println("Manual price check triggered");
        checkPriceAlerts();
    }
}
