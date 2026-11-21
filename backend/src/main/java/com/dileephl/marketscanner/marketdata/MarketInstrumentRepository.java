package com.dileephl.marketscanner.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketInstrumentRepository extends JpaRepository<MarketInstrumentEntity, String> {
    List<MarketInstrumentEntity> findTop30ByProviderAndExchangeAndTradingsymbolContainingIgnoreCaseOrderByTradingsymbolAsc(
            String provider, String exchange, String query);
    Optional<MarketInstrumentEntity> findByProviderAndInstrumentToken(String provider, long instrumentToken);
    List<MarketInstrumentEntity> findByProviderAndExchangeOrderByTradingsymbolAsc(String provider, String exchange);
}
