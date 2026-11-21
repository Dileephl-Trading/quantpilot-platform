package com.dileephl.marketscanner.journal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface PaperTradeRepository extends JpaRepository<PaperTradeEntity, UUID> {
    List<PaperTradeEntity> findByTradeDateOrderByCreatedAtDesc(LocalDate tradeDate);
    long countByTradeDate(LocalDate tradeDate);
    long countByStatus(TradeStatus status);
    List<PaperTradeEntity> findByStatusIn(Collection<TradeStatus> statuses);
    boolean existsByTradeDateAndSymbolAndStatusIn(LocalDate tradeDate, String symbol, Collection<TradeStatus> statuses);
    java.util.Optional<PaperTradeEntity> findTopByTradeDateOrderByCreatedAtDesc(LocalDate tradeDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from paper_trade p where p.id = :id")
    java.util.Optional<PaperTradeEntity> findByIdForUpdate(@Param("id") UUID id);
}
