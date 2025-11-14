package com.cs484.steamaccountibilibuddy.service;

import com.cs484.steamaccountibilibuddy.entity.PriceAlert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {
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
            System.err.println("Cannot send email: recipient email is blank");
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
                    "Steam Accountabilibuddy Price Alert Service\n" +
                    "To manage your price alerts, log in to your account.",
                    alert.getGameName(),
                    currentPrice.toString(),
                    alert.getTargetPrice().toString(),
                    alert.getAppId()
            );

            message.setText(body);

            mailSender.send(message);
            System.out.println("Price drop email sent to " + toEmail + " for game " + alert.getGameName());

        } catch (Exception e) {
            System.err.println("Failed to send email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("Cannot send email: recipient email is blank");
            return;
        }

        if (alertsWithPrices == null || alertsWithPrices.isEmpty()) {
            System.err.println("Cannot send email: no alerts provided");
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

                body.append(String.format(
                        "• %s\n" +
                        "  Now: $%s (Target: $%s)\n" +
                        "  Steam: https://store.steampowered.com/app/%d\n\n",
                        alert.getGameName(),
                        currentPrice.toString(),
                        alert.getTargetPrice().toString(),
                        alert.getAppId()
                ));
            }

            body.append("---\n");
            body.append("Steam Accountabilibuddy Price Alert Service\n");
            body.append("To manage your price alerts, log in to your account.");

            message.setText(body.toString());

            mailSender.send(message);
            System.out.println("Batched price drop email sent to " + toEmail + " for " + gameCount + " games");

        } catch (Exception e) {
            System.err.println("Failed to send batched email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
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
            message.setSubject("Test Email from Steam Accountabilibuddy");
            message.setText("This is a test email to confirm your email settings are working correctly.");

            mailSender.send(message);
            System.out.println("Test email sent to " + toEmail);

        } catch (Exception e) {
            System.err.println("Failed to send test email to " + toEmail + ": " + e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }
}
