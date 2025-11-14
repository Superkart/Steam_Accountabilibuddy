package com.cs484.steamaccountibilibuddy.scheduler;

import com.cs484.steamaccountibilibuddy.entity.PriceAlert;
import com.cs484.steamaccountibilibuddy.entity.User;
import com.cs484.steamaccountibilibuddy.service.EmailService;
import com.cs484.steamaccountibilibuddy.service.PriceAlertService;
import com.cs484.steamaccountibilibuddy.service.SteamBatchService;
import com.cs484.steamaccountibilibuddy.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

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
            Map<Integer, BigDecimal> allPrices = new java.util.HashMap<>();

            for (int i = 0; i < appIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, appIds.size());
                List<Integer> batch = appIds.subList(i, end);

                System.out.println("Fetching batch " + (i / batchSize + 1) + " (" + batch.size() + " games)...");
                Map<Integer, BigDecimal> batchPrices = steamBatchService.batchGetPrices(batch, "US");
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
            for (PriceAlert alert : allAlerts) {
                BigDecimal currentPrice = allPrices.get(alert.getAppId());
                if (currentPrice != null) {
                    pricesChecked++;
                    // Update alert with current price (also resets notification if price went above target)
                    priceAlertService.updateAlertPrice(alert, currentPrice);
                }
            }

            // Step 2: Group alerts by user for batched notifications
            Map<String, Map<PriceAlert, BigDecimal>> alertsByUser = new HashMap<>();

            for (PriceAlert alert : allAlerts) {
                BigDecimal currentPrice = allPrices.get(alert.getAppId());

                // Check if price is below target AND we haven't notified yet
                if (currentPrice != null &&
                    currentPrice.compareTo(alert.getTargetPrice()) < 0 &&
                    alert.getLastNotificationSent() == null) {

                    // Add to user's alert map
                    alertsByUser
                        .computeIfAbsent(alert.getSteamId(), k -> new HashMap<>())
                        .put(alert, currentPrice);
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
    public void manualPriceCheck() {
        System.out.println("Manual price check triggered");
        checkPriceAlerts();
    }
}
