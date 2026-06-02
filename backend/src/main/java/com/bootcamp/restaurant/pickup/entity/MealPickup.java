package com.bootcamp.restaurant.pickup.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

/**
 * 對應資料庫 meal_pickups 表
 * FK: item_id → order_items.item_id (同學4的表)
 * FK: admin_id → admin_users.admin_id (同學1的表)
 */
@Data
@Entity
@Table(name = "meal_pickups")
public class MealPickup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pickup_id")
    private Long pickupId;

    // FK → OrderItemEntity.itemId（同學4）
    @Column(name = "item_id", nullable = false, unique = true)
    private Long itemId;

    // FK → Admin_Users.admin_id（同學1），核銷時填入
    @Column(name = "admin_id")
    private Long adminId;

    // 核銷碼：手機後4碼 + "-" + 訂單末3碼，例如 "5678-023"
    @Column(name = "method", length = 10)
    private String method;

    // 預計取餐時間（來自 Menus.supply_time，同學2負責）
    @Column(name = "expected_time")
    private Instant expectedTime;

    // 實際取餐時間，未取就係 NULL
    @Column(name = "actual_time")
    private Instant actualTime;

    // false = 未通知 Admin, true = 已通知
    @Column(name = "admin_notified", nullable = false)
    private Boolean adminNotified = false;
}
