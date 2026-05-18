package com.steamlens.service;

import com.steamlens.entity.PriceAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send a price drop notification email to a user.
     *
     * @param toEmail The recipient's email address
     * @param alert The PriceAlert that triggered the notification
     * @param currentPrice The current price of the game
     * @param username The user's Steam username (optional)
     */
    public void sendPriceDropNotification(String toEmail, PriceAlert alert, BigDecimal currentPrice, String username) {
        if (toEmail == null || toEmail.isBlank()) {
            logger.warn("Cannot send price drop email: recipient email is blank");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Price Alert: " + alert.getGameName() + " is now $" + currentPrice);

            String greeting = (username != null && !username.isBlank())
                    ? "Hi " + username + "!\n\n"
                    : "Good news!\n\n";

            String body = String.format(
                    greeting +
                    "The game '%s' has dropped to $%s, which is below your target price of $%s.\n\n" +
                    "Get it on Steam: https://store.steampowered.com/app/%d\n\n" +
                    "---\n" +
                    "SteamLens Price Alert Service\n" +
                    "To manage your price alerts, log in to your account.",
                    alert.getGameName(),
                    currentPrice.toString(),
                    alert.getTargetPrice().toString(),
                    alert.getAppId()
            );

            message.setText(body);

            mailSender.send(message);
            logger.info("Price drop email sent to {} for game {}", toEmail, alert.getGameName());

        } catch (org.springframework.mail.MailException e) {
            logger.error("Failed to send price drop email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    /**
     * Send a price drop notification email to a user (without username).
     *
     * @param toEmail The recipient's email address
     * @param alert The PriceAlert that triggered the notification
     * @param currentPrice The current price of the game
     */
    public void sendPriceDropNotification(String toEmail, PriceAlert alert, BigDecimal currentPrice) {
        sendPriceDropNotification(toEmail, alert, currentPrice, null);
    }

    /**
     * Send a batched price drop notification email with multiple games on sale.
     * This prevents users from receiving multiple emails when several games drop in price.
     *
     * @param toEmail The recipient's email address
     * @param alertsWithPrices Map of PriceAlert to current price for all games on sale
     * @param username The user's Steam username (optional)
     */
    public void sendBatchedPriceDropNotification(String toEmail, Map<PriceAlert, BigDecimal> alertsWithPrices, String username) {
        if (toEmail == null || toEmail.isBlank()) {
            logger.warn("Cannot send batched price drop email: recipient email is blank");
            return;
        }

        if (alertsWithPrices == null || alertsWithPrices.isEmpty()) {
            logger.warn("Cannot send batched price drop email: no alerts provided");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);

            int gameCount = alertsWithPrices.size();
            String subject = gameCount == 1
                    ? "Price Alert: 1 game on sale!"
                    : String.format("Price Alert: %d games on sale!", gameCount);
            message.setSubject(subject);

            String greeting = (username != null && !username.isBlank())
                    ? "Hi " + username + "!\n\n"
                    : "Good news!\n\n";

            StringBuilder body = new StringBuilder(greeting);

            if (gameCount == 1) {
                body.append("A game on your wishlist has dropped below your target price:\n\n");
            } else {
                body.append(String.format("%d games on your wishlist have dropped below your target prices:\n\n", gameCount));
            }

            // List each game with its price details
            for (Map.Entry<PriceAlert, BigDecimal> entry : alertsWithPrices.entrySet()) {
                PriceAlert alert = entry.getKey();
                BigDecimal currentPrice = entry.getValue();

                body.append("""
                        • %s
                          Now: $%s (Target: $%s)
                          Steam: https://store.steampowered.com/app/%d

                        """.formatted(
                        alert.getGameName(),
                        currentPrice.toString(),
                        alert.getTargetPrice().toString(),
                        alert.getAppId()
                ));
            }

            body.append("---\n");
            body.append("SteamLens Price Alert Service\n");
            body.append("To manage your price alerts, log in to your account.");

            message.setText(body.toString());

            mailSender.send(message);
            logger.info("Batched price drop email sent to {} for {} games", toEmail, gameCount);

        } catch (org.springframework.mail.MailException e) {
            logger.error("Failed to send batched email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    /**
     * Test method to send a test email.
     *
     * @param toEmail The recipient's email address
     */
    public void sendTestEmail(String toEmail) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Test Email from SteamLens");
            message.setText("This is a test email to confirm your email settings are working correctly.");

            mailSender.send(message);
            logger.info("Test email sent to {}", toEmail);

        } catch (org.springframework.mail.MailException e) {
            logger.error("Failed to send test email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Email sending failed", e);
        }
    }
}
