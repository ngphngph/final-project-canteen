package com.restaurant.chat;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@FeignClient(name = "chat-menu-client", url = "http://127.0.0.1:8080")
public interface MenuClient {

    @GetMapping("/api/menu/dishes/today")
    List<Map<String, Object>> getDishesToday();

    @GetMapping("/api/menu/drinks/today")
    List<Map<String, Object>> getDrinkesToday();

    @GetMapping("/api/menu/order-window")
    Map<String, Object> getOrderWindow();
}
