package com.steamlens.service;

import com.steamlens.entity.SharedLink;
import com.steamlens.repository.SharedLinkRepository;
import com.steamlens.dto.WishlistEntryDto;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

@Service
public class SharedLinkService {
    private final SharedLinkRepository repository;
    private final SteamService steamService;
    private final ObjectMapper objectMapper;

    public SharedLinkService(SharedLinkRepository repository, SteamService steamService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.steamService = steamService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a share link for the user's wishlist. Returns the generated UUID.
     */
    public String createWishlistShare(String steamId) {
        List<WishlistEntryDto> wishlist = steamService.getWishlist(steamId);

        try {
            String snapshot = objectMapper.writeValueAsString(wishlist);
            String uuid = UUID.randomUUID().toString();

            SharedLink link = new SharedLink();
            link.setUuid(uuid);
            link.setSteamId(steamId);
            link.setType("wishlist");
            link.setSnapshotData(snapshot);

            repository.save(link);
            return uuid;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create share link", e);
        }
    }

    /**
     * Retrieve shared data for a given uuid. Returns a map containing metadata and the parsed snapshot.
     */
    public Optional<Map<String, Object>> getSharedData(String uuid) {
        Optional<SharedLink> opt = repository.findByUuid(uuid);
        if (opt.isEmpty()) return Optional.empty();

        SharedLink link = opt.get();
        try {
            List<WishlistEntryDto> wishlist = objectMapper.readValue(link.getSnapshotData(), new TypeReference<List<WishlistEntryDto>>(){});
            Map<String, Object> out = new HashMap<>();
            out.put("uuid", link.getUuid());
            out.put("type", link.getType());
            out.put("createdAt", link.getCreatedAt());
            out.put("wishlist", wishlist);
            return Optional.of(out);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
