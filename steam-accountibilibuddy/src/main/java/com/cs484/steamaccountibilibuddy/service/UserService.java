package com.cs484.steamaccountibilibuddy.service;

import com.cs484.steamaccountibilibuddy.entity.User;
import com.cs484.steamaccountibilibuddy.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates or updates a user in the database.
     * @param steamId The Steam ID
     * @return The persisted User entity
     */
    @Transactional
    public User createOrUpdateUser(String steamId) {
        Optional<User> existingUser = userRepository.findBySteamId(steamId);
        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        User newUser = new User(steamId);
        return userRepository.save(newUser);
    }

    /**
     * Creates or updates a user in the database with username.
     * @param steamId The Steam ID
     * @param username The Steam username
     * @return The persisted User entity
     */
    @Transactional
    public User createOrUpdateUser(String steamId, String username) {
        Optional<User> existingUser = userRepository.findBySteamId(steamId);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // Update username if it has changed
            if (username != null && !username.equals(user.getUsername())) {
                user.setUsername(username);
                return userRepository.save(user);
            }
            return user;
        }

        User newUser = new User();
        newUser.setSteamId(steamId);
        newUser.setUsername(username);
        return userRepository.save(newUser);
    }

    /**
     * Gets a user by Steam ID.
     * @param steamId The Steam ID
     * @return Optional containing the User if found
     */
    public Optional<User> getUserBySteamId(String steamId) {
        return userRepository.findBySteamId(steamId);
    }

    /**
     * Updates a user's email address.
     * @param steamId The Steam ID
     * @param email The new email address
     * @return The updated User entity
     */
    @Transactional
    public User updateUserEmail(String steamId, String email) {
        User user = userRepository.findBySteamId(steamId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with steamId: " + steamId));

        user.setEmail(email);
        return userRepository.save(user);
    }

    /**
     * Checks if a user exists by Steam ID.
     * @param steamId The Steam ID
     * @return true if user exists, false otherwise
     */
    public boolean userExists(String steamId) {
        return userRepository.existsBySteamId(steamId);
    }
}
