package com.dileephl.marketscanner.live;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LiveSignalRepository extends JpaRepository<LiveSignalEntity, UUID> {
    boolean existsByProviderAndInstrumentTokenAndCandleStart(String provider, long instrumentToken, OffsetDateTime candleStart);
    List<LiveSignalEntity> findTop25ByOrderByGeneratedAtDesc();
    List<LiveSignalEntity> findTop200ByOrderByGeneratedAtDesc();
    List<LiveSignalEntity> findByGeneratedAtAfterOrderByGeneratedAtDesc(OffsetDateTime cutoff);
}
