package com.achiever.repository;

import com.achiever.entity.StravaConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StravaConnectionRepository extends JpaRepository<StravaConnection, UUID> {

    Optional<StravaConnection> findByAthleteId(Long athleteId);

    boolean existsByAthleteId(Long athleteId);
}
