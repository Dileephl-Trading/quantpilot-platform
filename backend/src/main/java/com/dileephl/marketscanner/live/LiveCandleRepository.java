package com.dileephl.marketscanner.live;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LiveCandleRepository extends JpaRepository<LiveCandleEntity, UUID> {
    boolean existsByProviderAndInstrumentTokenAndCandleStart(String provider, long instrumentToken, OffsetDateTime candleStart);
    List<LiveCandleEntity> findTop100ByOrderByCandleStartDesc();
    List<LiveCandleEntity> findByGeneratedAtAfterOrderByGeneratedAtDesc(OffsetDateTime cutoff);
}
