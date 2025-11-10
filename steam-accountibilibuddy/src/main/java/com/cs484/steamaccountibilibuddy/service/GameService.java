package com.cs484.steamaccountibilibuddy.service;

import com.cs484.steamaccountibilibuddy.dto.GameDetailsDto;
import com.cs484.steamaccountibilibuddy.entity.Game;
import com.cs484.steamaccountibilibuddy.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GameService {
    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    /**
     * Get game from cache by appId.
     * Returns Optional containing GameDetailsDto if found in cache.
     */
    public Optional<GameDetailsDto> getGameFromCache(Integer appId) {
        if (appId == null) return Optional.empty();

        return gameRepository.findByAppId(appId)
                .map(this::convertToDto);
    }

    /**
     * Save or update game details in the cache.
     * @param appId The Steam app ID
     * @param gameDetails The game details to cache
     */
    @Transactional
    public void cacheGameDetails(Integer appId, GameDetailsDto gameDetails) {
        if (appId == null || gameDetails == null) return;

        Optional<Game> existingGame = gameRepository.findByAppId(appId);

        Game game;
        if (existingGame.isPresent()) {
            game = existingGame.get();
        } else {
            game = new Game();
            game.setAppId(appId);
        }

        game.setName(gameDetails.getName());
        game.setTags(convertTagsToString(gameDetails.getTags()));
        // Note: We don't store imgIconUrl here since it's generated from appId
        // If you want to store it, you can add it to GameDetailsDto and pass it here

        gameRepository.save(game);
    }

    /**
     * Convert Game entity to GameDetailsDto.
     */
    private GameDetailsDto convertToDto(Game game) {
        String name = game.getName();
        List<String> tags = convertStringToTags(game.getTags());
        return new GameDetailsDto(name, tags);
    }

    /**
     * Convert list of tags to comma-separated string for database storage.
     */
    private String convertTagsToString(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return String.join(",", tags);
    }

    /**
     * Convert comma-separated string to list of tags.
     */
    private List<String> convertStringToTags(String tagsString) {
        if (tagsString == null || tagsString.isBlank()) return Collections.emptyList();
        return Arrays.stream(tagsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Check if a game exists in cache.
     */
    public boolean isGameCached(Integer appId) {
        return appId != null && gameRepository.existsByAppId(appId);
    }
}
