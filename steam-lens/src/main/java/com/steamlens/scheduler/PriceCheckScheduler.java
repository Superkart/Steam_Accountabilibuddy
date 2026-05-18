package com.steamlens.scheduler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(PriceCheckScheduler.class);
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
        logger.info("Starting daily price check job...");

        List<PriceAlert> allAlerts = priceAlertService.getAllAlerts();
        logger.info("Found {} price alerts to check", allAlerts.size());

        if (allAlerts.isEmpty()) {
            logger.info("No price alerts to check");
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

            logger.info("Fetching prices for {} unique games using batch API", appIds.size());

            // Split into batches of 500 to avoid overwhelming the API
            int batchSize = 500;
            Map<Integer, PriceInfo> allPrices = new java.util.HashMap<>();

            for (int i = 0; i < appIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, appIds.size());
                List<Integer> batch = appIds.subList(i, end);

                logger.debug("Fetching batch {} ({} games)", i / batchSize + 1, batch.size());
                Map<Integer, PriceInfo> batchPrices = steamBatchService.batchGetPrices(batch, "US");
                allPrices.putAll(batchPrices);

                // Small delay between batches to be respectful to Steam's API
                if (end < appIds.size()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Price check interrupted during batch delay", e);
                        break;
                    }
                }
            }

            logger.info("Successfully fetched {} prices", allPrices.size());

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
                logger.warn("Skipped updating currentPrice for appIds (no data): {}", skippedIds);
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
                            logger.info("Sent batched notification to {} for {} games", user.getEmail(), userAlerts.size());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error sending batched notification for user {}: {}", steamId, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            logger.error("Error during batch price check", e);
        }

        logger.info("Price check job completed. Checked {} prices, sent {} batched notifications.", pricesChecked, emailsSent);
    }

    /**
     * Manual trigger for testing (can be called via a controller if needed).
     */
    @org.springframework.transaction.annotation.Transactional
    public void manualPriceCheck() {
        logger.info("Manual price check triggered");
        checkPriceAlerts();
    }
}
