package com.dileephl.marketscanner.live;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntryEntity, UUID> {
    List<WatchlistEntryEntity> findByProviderAndEnabledTrueOrderBySymbolAsc(String provider);
    List<WatchlistEntryEntity> findByProviderAndAutoManagedTrue(String provider);
    Optional<WatchlistEntryEntity> findByProviderAndInstrumentToken(String provider, long instrumentToken);
}
