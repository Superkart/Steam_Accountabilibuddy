package com.cs484.steamaccountibilibuddy.service;

import com.cs484.steamaccountibilibuddy.entity.PriceAlert;
import com.cs484.steamaccountibilibuddy.entity.User;
import com.cs484.steamaccountibilibuddy.repository.PriceAlertRepository;
import com.cs484.steamaccountibilibuddy.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PriceAlertService {
    private final PriceAlertRepository priceAlertRepository;
    private final UserRepository userRepository;

    public PriceAlertService(PriceAlertRepository priceAlertRepository, UserRepository userRepository) {
        this.priceAlertRepository = priceAlertRepository;
        this.userRepository = userRepository;
    }

    /**
     * Create or update a price alert for a game.
     * Only creates/updates if targetPrice > 0 and user has an email.
     *
     * @param steamId The user's Steam ID
     * @param appId The game's app ID
     * @param gameName The game's name
     * @param targetPrice The target price threshold
     * @return The created/updated PriceAlert
     * @throws IllegalStateException if user doesn't have an email set
     * @throws IllegalArgumentException if targetPrice <= 0
     */
    @Transactional
    public PriceAlert createOrUpdatePriceAlert(String steamId, Integer appId, String gameName, BigDecimal targetPrice) {
        // Validate targetPrice
        if (targetPrice == null || targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Target price must be greater than $0");
        }

        // Check if user has email
        User user = userRepository.findBySteamId(steamId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Please set your email address before creating price alerts");
        }

        // Check if alert already exists
        Optional<PriceAlert> existing = priceAlertRepository.findBySteamIdAndAppId(steamId, appId);

        PriceAlert alert;
        if (existing.isPresent()) {
            // Update existing alert
            alert = existing.get();
            alert.setTargetPrice(targetPrice);
            alert.setGameName(gameName);
        } else {
            // Create new alert
            alert = new PriceAlert();
            alert.setSteamId(steamId);
            alert.setAppId(appId);
            alert.setGameName(gameName);
            alert.setTargetPrice(targetPrice);
        }

        return priceAlertRepository.save(alert);
    }

    /**
     * Get all price alerts for a user.
     *
     * @param steamId The user's Steam ID
     * @return List of PriceAlert entities
     */
    public List<PriceAlert> getAllAlertsForUser(String steamId) {
        return priceAlertRepository.findBySteamId(steamId);
    }

    /**
     * Delete a price alert.
     *
     * @param steamId The user's Steam ID
     * @param appId The game's app ID
     */
    @Transactional
    public void deletePriceAlert(String steamId, Integer appId) {
        priceAlertRepository.deleteBySteamIdAndAppId(steamId, appId);
    }

    /**
     * Get all price alerts (for background job).
     *
     * @return List of all PriceAlert entities
     */
    public List<PriceAlert> getAllAlerts() {
        return priceAlertRepository.findAll();
    }

    /**
     * Update the current price and last checked timestamp for an alert.
     *
     * @param alert The PriceAlert to update
     * @param currentPrice The current price from Steam
     * @return The updated PriceAlert
     */
    @Transactional
    public PriceAlert updateAlertPrice(PriceAlert alert, BigDecimal currentPrice) {
        alert.setCurrentPrice(currentPrice);
        alert.setLastChecked(java.time.LocalDateTime.now());
        return priceAlertRepository.save(alert);
    }
}
