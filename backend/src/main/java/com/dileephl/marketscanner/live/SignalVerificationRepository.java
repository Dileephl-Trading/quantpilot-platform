package com.dileephl.marketscanner.live;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SignalVerificationRepository extends JpaRepository<SignalVerificationEntity, UUID> {
    Optional<SignalVerificationEntity> findByProviderAndInstrumentTokenAndCandleStart(
            String provider, long instrumentToken, OffsetDateTime candleStart);

    Page<SignalVerificationEntity> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            OffsetDateTime cutoff, Pageable pageable);
}
