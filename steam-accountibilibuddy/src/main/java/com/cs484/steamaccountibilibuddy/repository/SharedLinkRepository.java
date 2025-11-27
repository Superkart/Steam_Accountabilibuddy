package com.cs484.steamaccountibilibuddy.repository;

import com.cs484.steamaccountibilibuddy.entity.SharedLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {
    Optional<SharedLink> findByUuid(String uuid);
}
