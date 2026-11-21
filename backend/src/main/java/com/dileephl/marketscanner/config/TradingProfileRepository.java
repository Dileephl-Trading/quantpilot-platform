package com.dileephl.marketscanner.config;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface TradingProfileRepository extends JpaRepository<TradingProfileEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from trading_profile p where p.id = :id")
    Optional<TradingProfileEntity> findByIdForUpdate(@Param("id") Long id);
}
