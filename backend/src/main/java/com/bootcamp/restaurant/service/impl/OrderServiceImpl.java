package com.bootcamp.restaurant.service.impl;

import com.bootcamp.restaurant.dto.OrderItemResp;
import com.bootcamp.restaurant.dto.OrderResp;
import com.bootcamp.restaurant.entity.OrderEntity;
import com.bootcamp.restaurant.entity.OrderItemEntity;
import com.bootcamp.restaurant.enums.OrderStatus;
import com.bootcamp.restaurant.enums.PickupStatus;
import com.bootcamp.restaurant.exception.ResourceNotFoundException;
import com.bootcamp.restaurant.mapper.OrderMapper;
import com.bootcamp.restaurant.model.OrderCreateReq;
import com.bootcamp.restaurant.pickup.dto.PickupCreateRequest;
import com.bootcamp.restaurant.pickup.service.MealPickupService;
import com.bootcamp.restaurant.repository.OrderItemRepository;
import com.bootcamp.restaurant.repository.OrderRepository;
import com.bootcamp.restaurant.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderMapper orderMapper;

    // ⭐ 連接 Part 5：取餐核銷系統
    @Autowired
    private MealPickupService mealPickupService;

    @Override
    public List<OrderResp> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId)
                .stream()
                .map(orderMapper::map)
                .collect(Collectors.toList());
    }

    @Override
    public OrderResp getOrderById(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return orderMapper.map(order);
    }

    @Override
    @Transactional
    public OrderResp createOrder(OrderCreateReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        for (OrderCreateReq.OrderItemReq item : req.items()) {
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("quantity must be positive, got: " + item.quantity());
            }
            if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("unitPrice must be positive, got: " + item.unitPrice());
            }
            if (item.specialNote() != null && item.specialNote().length() > 200) {
                throw new IllegalArgumentException(
                        "specialNote must not exceed 200 characters, got: " + item.specialNote().length());
            }
        }

        // Save order shell first to get the generated orderId for FK in order items
        OrderEntity order = OrderEntity.builder()
                .userId(req.userId())
                .orderStatus(OrderStatus.PENDING_PAY)
                .createdAt(LocalDateTime.now())
                .build();
        OrderEntity savedOrder = orderRepository.save(order);

        // Build items and accumulate totals
        List<OrderItemEntity> items = new ArrayList<>();
        BigDecimal totalAmt = BigDecimal.ZERO;
        int totalQty = 0;

        for (OrderCreateReq.OrderItemReq itemReq : req.items()) {
            totalAmt = totalAmt.add(itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity())));
            totalQty += itemReq.quantity();

            items.add(OrderItemEntity.builder()
                    .orderEntity(savedOrder)
                    .menuId(itemReq.menuId())
                    .quantity(itemReq.quantity())
                    .unitPrice(itemReq.unitPrice())
                    .pickupStatus(PickupStatus.PENDING)
                    .specialNote(itemReq.specialNote())
                    .build());
        }

        // Apply deposit rule: totalQty >= 4 → depositAmt = 50
        BigDecimal depositAmt = totalQty >= 4 ? BigDecimal.valueOf(50) : BigDecimal.ZERO;

        savedOrder.setTotalAmt(totalAmt);
        savedOrder.setTotalQty(totalQty);
        savedOrder.setDepositAmt(depositAmt);
        orderRepository.save(savedOrder);

        orderItemRepository.saveAll(items);

        return orderMapper.map(savedOrder);
    }

    @Override
    @Transactional
    public OrderResp updateOrderStatus(Long orderId, String status) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid order status: " + status);
        }

        order.setOrderStatus(newStatus);
        OrderEntity saved = orderRepository.save(order);

        // ⭐ 連接 Part 5：付款成功後，為每個 OrderItem 建立取餐記錄
        // DEPOSIT_PAID = 已付訂金，FULLY_PAID = 全額付清，兩個情況都要建立取餐記錄
        if (newStatus == OrderStatus.DEPOSIT_PAID || newStatus == OrderStatus.FULLY_PAID) {
            List<OrderItemEntity> items = orderItemRepository.findByOrderEntity_OrderId(orderId);
            for (OrderItemEntity item : items) {

                // 核銷碼 = 手機後4碼 + "-" + 訂單末3碼
                // ⚠️ 注意：手機號碼需要同學1（User系統）提供
                // 暫時用 userId 末4碼代替，等同學1做好再換
                String phoneLast4 = String.format("%04d", order.getUserId() % 10000);
                String orderLast3 = String.format("%03d", orderId % 1000);
                String method = phoneLast4 + "-" + orderLast3;

                PickupCreateRequest pickupReq = new PickupCreateRequest();
                pickupReq.setItemId(item.getItemId());
                pickupReq.setMethod(method);
                // ⚠️ expectedTime 需要同學2（Menu系統）提供 supply_time
                // 暫時設為訂單建立時間 + 2小時，等同學2做好再換
                pickupReq.setExpectedTime(
                    order.getCreatedAt().plusHours(2)
                         .toInstant(java.time.ZoneOffset.UTC)
                );

                mealPickupService.createPickup(pickupReq);
            }
        }

        return orderMapper.map(saved);
    }

    @Override
    public List<OrderItemResp> getOrderItems(Long orderId) {
        return orderItemRepository.findByOrderEntity_OrderId(orderId)
                .stream()
                .map(orderMapper::map)
                .collect(Collectors.toList());
    }
}
