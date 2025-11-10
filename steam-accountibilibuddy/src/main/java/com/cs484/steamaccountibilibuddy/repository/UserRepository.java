package com.cs484.steamaccountibilibuddy.repository;

import com.cs484.steamaccountibilibuddy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findBySteamId(String steamId);
    boolean existsBySteamId(String steamId);
}
