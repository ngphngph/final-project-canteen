package com.bootcamp.restaurant.service.impl;

import com.bootcamp.restaurant.dto.OrderItemResp;
import com.bootcamp.restaurant.dto.OrderResp;
import com.bootcamp.restaurant.entity.OrderEntity;
import com.bootcamp.restaurant.entity.OrderItemEntity;
import com.bootcamp.restaurant.enums.OrderStatus;
import com.bootcamp.restaurant.enums.PickupStatus;
import com.bootcamp.restaurant.mapper.OrderMapper;
import com.bootcamp.restaurant.model.OrderCreateReq;
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
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        return orderMapper.map(order);
    }

    @Override
    @Transactional
    public OrderResp createOrder(OrderCreateReq req) {
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
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setOrderStatus(OrderStatus.valueOf(status));
        return orderMapper.map(orderRepository.save(order));
    }

    @Override
    public List<OrderItemResp> getOrderItems(Long orderId) {
        return orderItemRepository.findByOrderEntity_OrderId(orderId)
                .stream()
                .map(orderMapper::map)
                .collect(Collectors.toList());
    }
}
