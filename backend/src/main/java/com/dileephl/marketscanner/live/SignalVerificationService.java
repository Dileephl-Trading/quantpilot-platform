package com.dileephl.marketscanner.live;

import com.dileephl.marketscanner.api.PagedResponse;
import com.dileephl.marketscanner.service.SignalEngine;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class SignalVerificationService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final ZoneOffset INDIA_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);
    private final SignalVerificationRepository repository;

    public SignalVerificationService(SignalVerificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String provider, long instrumentToken, String symbol,
                       SignalEngine.Candle candle, SignalEngine.TradeSignal signal,
                       String decision, String analysis, String orderId) {
        if (signal == null || signal.type() == SignalEngine.SignalType.NO_TRADE) return;
        SignalVerificationEntity entity = repository
                .findByProviderAndInstrumentTokenAndCandleStart(provider, instrumentToken, candle.timestamp())
                .orElseGet(() -> new SignalVerificationEntity(provider, instrumentToken, symbol,
                        candle.timestamp(), signal.type().name(), signal.referencePrice(), signal.vwap(),
                        signal.invalidationReference(), signal.openingRangeHigh(), signal.openingRangeLow(),
                        signal.averageVolume(), signal.volumeThreshold(), analysis));
        entity.update(decision, analysis, orderId);
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SignalVerificationView> page(int days, int page, int pageSize) {
        int safeDays = Math.max(1, Math.min(days, 7));
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        OffsetDateTime cutoff = OffsetDateTime.now(INDIA_OFFSET).minusDays(safeDays);
        return PagedResponse.from(repository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                cutoff, PageRequest.of(safePage, safePageSize)), SignalVerificationEntity::view);
    }
}
