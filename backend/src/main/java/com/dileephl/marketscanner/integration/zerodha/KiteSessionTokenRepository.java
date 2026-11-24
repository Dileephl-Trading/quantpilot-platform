package com.dileephl.marketscanner.integration.zerodha;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KiteSessionTokenRepository extends JpaRepository<KiteSessionTokenEntity, Short> {
}