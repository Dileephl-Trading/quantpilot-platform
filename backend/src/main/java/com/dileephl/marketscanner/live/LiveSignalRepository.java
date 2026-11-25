package com.dileephl.marketscanner.live;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LiveSignalRepository extends JpaRepository<LiveSignalEntity, UUID> {
    boolean existsByProviderAndInstrumentTokenAndCandleStart(String provider, long instrumentToken, OffsetDateTime candleStart);
    List<LiveSignalEntity> findTop25ByOrderByGeneratedAtDesc();
    List<LiveSignalEntity> findTop200ByOrderByGeneratedAtDesc();
    List<LiveSignalEntity> findByGeneratedAtAfterOrderByGeneratedAtDesc(OffsetDateTime cutoff);

    @Query("""
            select e from LiveSignalEntity e
            where e.generatedAt >= :cutoff
              and (:symbol = '' or lower(e.symbol) = lower(:symbol))
              and (:type = '' or lower(e.signalType) = lower(:type))
            order by e.generatedAt desc
            """)
    Page<LiveSignalEntity> findSince(@Param("cutoff") OffsetDateTime cutoff,
                                     @Param("symbol") String symbol,
                                     @Param("type") String type,
                                     Pageable pageable);
}
