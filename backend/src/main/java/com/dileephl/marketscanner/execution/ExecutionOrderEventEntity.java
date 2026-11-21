package com.dileephl.marketscanner.execution;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "execution_order_event")
class ExecutionOrderEventEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 64) private String orderId;
    @Column(nullable = false, length = 24) private String status;
    @Column(nullable = false) private long filledQuantity;
    @Column(precision = 19, scale = 4) private java.math.BigDecimal averagePrice;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    @Column(nullable = false) private OffsetDateTime observedAt;

    protected ExecutionOrderEventEntity() { }

    static ExecutionOrderEventEntity from(BrokerOrderResponse response) {
        ExecutionOrderEventEntity event = new ExecutionOrderEventEntity();
        event.id = UUID.randomUUID();
        event.orderId = response.orderId();
        event.status = response.status().name();
        event.filledQuantity = response.filledQuantity();
        event.averagePrice = response.averagePrice();
        event.message = response.message() == null ? "" : response.message();
        event.observedAt = response.observedAt();
        return event;
    }

    ExecutionOrderEventView view() {
        return new ExecutionOrderEventView(orderId, status, filledQuantity, averagePrice, message, observedAt);
    }
}
