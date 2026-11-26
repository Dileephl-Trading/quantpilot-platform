package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.execution.ExecutionPreviewService;
import com.dileephl.marketscanner.execution.OrderPreview;
import com.dileephl.marketscanner.execution.BrokerOrderExecutionService;
import com.dileephl.marketscanner.execution.BrokerOrderRequest;
import com.dileephl.marketscanner.execution.BrokerOrderResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/execution")
public class ExecutionController {
    private final ExecutionPreviewService previews;
    private final BrokerOrderExecutionService orders;
    public ExecutionController(ExecutionPreviewService previews, BrokerOrderExecutionService orders) { this.previews = previews; this.orders = orders; }
    @PostMapping("/preview")
    public OrderPreview preview(@RequestBody ExecutionPreviewService.OrderRequest request) { return previews.preview(request); }

    @PostMapping("/orders")
    public BrokerOrderResponse place(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @RequestBody BrokerOrderRequest request) { return orders.place(idempotencyKey, request); }

    @GetMapping("/orders/{orderId}")
    public BrokerOrderResponse status(@PathVariable String orderId) { return orders.status(orderId); }

    @GetMapping("/orders")
    public java.util.List<BrokerOrderResponse> trackedOrders() { return orders.trackedOrders(); }

    @GetMapping("/orders/page")
    public PagedResponse<com.dileephl.marketscanner.execution.ExecutionOrderView> orderPage(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return orders.trackedOrdersPage(days, page, pageSize);
    }

    @GetMapping("/orders/history/page")
    public PagedResponse<com.dileephl.marketscanner.execution.ExecutionOrderHistoryView> orderHistoryPage(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return orders.trackedOrderHistoryPage(days, page, pageSize);
    }

    @GetMapping("/orders/{orderId}/events")
    public java.util.List<com.dileephl.marketscanner.execution.ExecutionOrderEventView> events(@PathVariable String orderId) {
        return orders.events(orderId);
    }

    @DeleteMapping("/orders/{orderId}")
    public BrokerOrderResponse cancel(@PathVariable String orderId) { return orders.cancel(orderId); }

    @PutMapping("/orders/{orderId}")
    public BrokerOrderResponse modify(@PathVariable String orderId, @RequestBody BrokerOrderRequest request) { return orders.modify(orderId, request); }

    @PostMapping("/orders/reconcile")
    public java.util.List<BrokerOrderResponse> reconcile() {
        return orders.reconcileTrackedOrders();
    }

    @PostMapping("/orders/cancel-open")
    public java.util.List<BrokerOrderResponse> cancelOpen() {
        return orders.cancelTrackedOpenOrders();
    }
}
