package com.bootcamp.restaurant.pickup.controller;

import com.bootcamp.restaurant.pickup.dto.PickupCreateRequest;
import com.bootcamp.restaurant.pickup.dto.PickupResponse;
import com.bootcamp.restaurant.pickup.dto.PickupVerifyRequest;
import com.bootcamp.restaurant.pickup.service.MealPickupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pickups")
@RequiredArgsConstructor
public class MealPickupController {

    private final MealPickupService service;

    // ⭐ 同學4專用：訂單付款成功後建立取餐記錄
    @PostMapping("/create")
    public ResponseEntity<PickupResponse> create(@Valid @RequestBody PickupCreateRequest req) {
        return ResponseEntity.status(201).body(service.createPickup(req));
    }

    // Admin 核銷取餐
    @PostMapping("/verify")
    public ResponseEntity<PickupResponse> verify(@Valid @RequestBody PickupVerifyRequest req) {
        return ResponseEntity.ok(service.verifyPickup(req));
    }

    // 查詢某個 order_item 的取餐狀態
    @GetMapping("/item/{itemId}")
    public ResponseEntity<PickupResponse> getByItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(service.getByItemId(itemId));
    }

    // 查詢所有逾時未取餐
    @GetMapping("/overdue")
    public ResponseEntity<List<PickupResponse>> getOverdue() {
        return ResponseEntity.ok(service.getOverduePickups());
    }

    // 查詢所有未通知 Admin 的記錄
    @GetMapping("/unnotified")
    public ResponseEntity<List<PickupResponse>> getUnnotified() {
        return ResponseEntity.ok(service.getUnnotified());
    }

    // 標記已通知 Admin
    @PatchMapping("/{pickupId}/notify")
    public ResponseEntity<PickupResponse> markNotified(@PathVariable Long pickupId) {
        return ResponseEntity.ok(service.markAdminNotified(pickupId));
    }
}
