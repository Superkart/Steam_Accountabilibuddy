package com.steamlens.repository;

import com.steamlens.entity.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    List<PriceAlert> findBySteamId(String steamId);
    Optional<PriceAlert> findBySteamIdAndAppId(String steamId, Integer appId);
    void deleteBySteamIdAndAppId(String steamId, Integer appId);
    boolean existsBySteamIdAndAppId(String steamId, Integer appId);
}
