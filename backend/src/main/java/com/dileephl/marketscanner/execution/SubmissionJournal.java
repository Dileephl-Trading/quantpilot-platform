package com.dileephl.marketscanner.execution;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionJournal {
    private final JdbcTemplate db;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    public SubmissionJournal(JdbcTemplate db, com.fasterxml.jackson.databind.ObjectMapper mapper) { this.db = db; this.mapper = mapper; }
    public record Reservation(String tag, boolean fresh) {}

    // A broker timeout or a rollback of the caller must retain this reservation.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(String key, BrokerOrderRequest request, String parent) {
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String fingerprint = BrokerOrderFingerprint.of(request);
        String payload;
        try { payload = mapper.writeValueAsString(request); }
        catch (java.io.IOException error) { throw new IllegalArgumentException("Invalid broker request", error); }
        int inserted = db.update("""
                insert into broker_submission_intent(intent_key, fingerprint, broker_tag, parent_order_id, symbol, payload)
                values (?, ?, ?, ?, ?, ?) on conflict (intent_key) do nothing
                """, key, fingerprint, tag, parent, request.symbol(), payload);
        if (inserted == 1) return new Reservation(tag, true);
        return db.queryForObject("select fingerprint, broker_tag from broker_submission_intent where intent_key=?",
                (rs, i) -> {
                    if (!fingerprint.equals(rs.getString(1))) throw new IllegalArgumentException("Submission key payload changed");
                    return new Reservation(rs.getString(2), false);
                }, key);
    }
    public record Pending(String key, String tag, String parent, BrokerOrderRequest request) {}
    public java.util.List<Pending> pending() {
        return db.query("""
                select intent_key,broker_tag,parent_order_id,payload from broker_submission_intent i
                where not exists(select 1 from execution_request r where r.idempotency_key=i.intent_key)
                and not exists(select 1 from execution_request_archive a where a.idempotency_key=i.intent_key)
                """, (rs, i) -> {
            try { return new Pending(rs.getString(1), rs.getString(2), rs.getString(3),
                    mapper.readValue(rs.getString(4), BrokerOrderRequest.class)); }
            catch (java.io.IOException e) { throw new IllegalStateException("Invalid saved order intent", e); }
        });
    }
    public boolean hasUnresolvedOtherThan(String key) {
        return Boolean.TRUE.equals(db.queryForObject("""
                select exists(select 1 from broker_submission_intent i where i.intent_key <> ?
                and not exists(select 1 from execution_request r where r.idempotency_key=i.intent_key)
                and not exists(select 1 from execution_request_archive a where a.idempotency_key=i.intent_key))
                """, Boolean.class, key));
    }
}
