package com.cs484.steamaccountibilibuddy.service;

import com.cs484.steamaccountibilibuddy.entity.PriceAlert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
