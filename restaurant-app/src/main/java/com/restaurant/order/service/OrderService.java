package com.restaurant.order.service;

import com.restaurant.order.dto.OrderCreateReq;
import com.restaurant.order.dto.OrderItemResp;
import com.restaurant.order.dto.OrderResp;

import java.util.List;

public interface OrderService {
    List<OrderResp> getOrdersByUserId(Long userId);
    OrderResp getOrderById(Long orderId);
    OrderResp createOrder(OrderCreateReq req);
    OrderResp updateStatus(Long orderId, String status);
    List<OrderItemResp> getOrderItems(Long orderId);
}
