package com.restaurant.order.controller;

import com.restaurant.order.client.WalletClient;
import com.restaurant.order.client.dto.WalletClientResp;
import com.restaurant.order.client.dto.WalletDeductReq;
import com.restaurant.order.dto.OrderCreateReq;
import com.restaurant.order.dto.OrderItemResp;
import com.restaurant.order.dto.OrderResp;
import com.restaurant.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final WalletClient walletClient;   // Feign — calls WalletController via 127.0.0.1:8080

    @GetMapping("/user/{userId}")
    public List<OrderResp> getByUser(@PathVariable Long userId) {
        return orderService.getOrdersByUserId(userId);
    }

    @GetMapping("/{orderId}")
    public OrderResp getById(@PathVariable Long orderId) {
        return orderService.getOrderById(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResp create(@RequestBody OrderCreateReq req) {
        // 1. Create order record
        OrderResp order = orderService.createOrder(req);

        // 2. Determine amount to deduct
        BigDecimal deductAmt = order.depositAmt().compareTo(BigDecimal.ZERO) > 0
                ? order.depositAmt()
                : order.totalAmt();

        if (deductAmt.compareTo(BigDecimal.ZERO) > 0) {
            // 3. Call Wallet module via Feign (HTTP → 127.0.0.1:8080)
            WalletClientResp wallet = walletClient.getWalletByUserId(req.userId());
            String idempotencyKey = "order-" + order.orderId() + "-pay";
            walletClient.deduct(wallet.walletId(),
                    new WalletDeductReq(null, deductAmt, "Order payment", idempotencyKey));

            // 4. Update status after successful payment
            String newStatus = order.depositAmt().compareTo(BigDecimal.ZERO) > 0
                    ? "DEPOSIT_PAID" : "FULLY_PAID";
            order = orderService.updateStatus(order.orderId(), newStatus);
        }

        return order;
    }

    @PutMapping("/{orderId}/status")
    public OrderResp updateStatus(@PathVariable Long orderId, @RequestParam String status) {
        return orderService.updateStatus(orderId, status);
    }

    @GetMapping("/{orderId}/items")
    public List<OrderItemResp> getItems(@PathVariable Long orderId) {
        return orderService.getOrderItems(orderId);
    }
}
