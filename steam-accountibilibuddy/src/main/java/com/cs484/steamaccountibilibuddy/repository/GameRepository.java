package com.cs484.steamaccountibilibuddy.repository;

import com.cs484.steamaccountibilibuddy.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Integer> {
    Optional<Game> findByAppId(Integer appId);
    List<Game> findByAppIdIn(List<Integer> appIds);
    boolean existsByAppId(Integer appId);
}
