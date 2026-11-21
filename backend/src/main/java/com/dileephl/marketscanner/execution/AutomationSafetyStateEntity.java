package com.dileephl.marketscanner.execution;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "automation_safety_state")
class AutomationSafetyStateEntity {
    @Id private Integer id;
    @Column(nullable = false) private boolean uncertainBrokerState;
    @Column(nullable = false, columnDefinition = "TEXT") private String reason;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    protected AutomationSafetyStateEntity() { }
    static AutomationSafetyStateEntity initial() {
        AutomationSafetyStateEntity state = new AutomationSafetyStateEntity();
        state.id = 1; state.uncertainBrokerState = false; state.reason = ""; state.updatedAt = OffsetDateTime.now();
        return state;
    }
    boolean uncertainBrokerState() { return uncertainBrokerState; }
    void markUncertain(String value) { uncertainBrokerState = true; reason = value == null ? "unknown" : value; updatedAt = OffsetDateTime.now(); }
    void clear() { uncertainBrokerState = false; reason = ""; updatedAt = OffsetDateTime.now(); }
    String reason() { return reason; }
}
