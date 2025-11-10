package com.cs484.steamaccountibilibuddy.scheduler;

import com.cs484.steamaccountibilibuddy.entity.PriceAlert;
import com.cs484.steamaccountibilibuddy.entity.User;
import com.cs484.steamaccountibilibuddy.service.EmailService;
import com.cs484.steamaccountibilibuddy.service.PriceAlertService;
import com.cs484.steamaccountibilibuddy.service.PriceCheckService;
import com.cs484.steamaccountibilibuddy.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class PriceCheckScheduler {
    private final PriceAlertService priceAlertService;
    private final PriceCheckService priceCheckService;
    private final EmailService emailService;
    private final UserService userService;

    public PriceCheckScheduler(PriceAlertService priceAlertService,
                               PriceCheckService priceCheckService,
                               EmailService emailService,
                               UserService userService) {
        this.priceAlertService = priceAlertService;
        this.priceCheckService = priceCheckService;
        this.emailService = emailService;
        this.userService = userService;
    }

    /**
     * Scheduled job that runs once daily at 9 AM to check all price alerts.
     * Cron expression: "0 0 9 * * ?" means: second=0, minute=0, hour=9, every day
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkPriceAlerts() {
        System.out.println("Starting daily price check job...");

        List<PriceAlert> allAlerts = priceAlertService.getAllAlerts();
        System.out.println("Found " + allAlerts.size() + " price alerts to check");

        int emailsSent = 0;
        int pricesChecked = 0;

        for (PriceAlert alert : allAlerts) {
            try {
                // Fetch current price from Steam
                BigDecimal currentPrice = priceCheckService.getCurrentPrice(alert.getAppId());

                if (currentPrice != null) {
                    pricesChecked++;

                    // Update alert with current price
                    priceAlertService.updateAlertPrice(alert, currentPrice);

                    // Check if price is below target
                    if (currentPrice.compareTo(alert.getTargetPrice()) < 0) {
                        // Price is below target! Send notification
                        Optional<User> userOpt = userService.getUserBySteamId(alert.getSteamId());

                        if (userOpt.isPresent()) {
                            User user = userOpt.get();
                            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                                emailService.sendPriceDropNotification(user.getEmail(), alert, currentPrice, user.getUsername());
                                emailsSent++;
                            }
                        }
                    }
                }

                // Add a small delay to avoid overwhelming Steam API
                Thread.sleep(1000); // 1 second delay between requests

            } catch (Exception e) {
                System.err.println("Error checking price for alert " + alert.getId() + ": " + e.getMessage());
            }
        }

        System.out.println("Price check job completed. Checked " + pricesChecked + " prices, sent " + emailsSent + " notifications.");
    }

    /**
     * Manual trigger for testing (can be called via a controller if needed).
     */
    public void manualPriceCheck() {
        System.out.println("Manual price check triggered");
        checkPriceAlerts();
    }
}
