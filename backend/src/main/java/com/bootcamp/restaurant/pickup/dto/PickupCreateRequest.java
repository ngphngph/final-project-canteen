package com.bootcamp.restaurant.pickup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.time.Instant;

/**
 * 同學4 OrderService 訂單付款成功後，用呢個 DTO 建立取餐記錄
 *
 * 建立時機：
 * updateOrderStatus() 將狀態改為 FULLY_PAID 或 DEPOSIT_PAID 時，
 * 對每個 OrderItem 呼叫 MealPickupService.createPickup()
 */
@Data
public class PickupCreateRequest {

    @NotNull(message = "item_id 唔可以空")
    private Long itemId;

    @NotNull(message = "expected_time 唔可以空")
    private Instant expectedTime;

    // 格式：手機後4碼 + "-" + 訂單末3碼，例如 "5678-023"
    @NotBlank(message = "核銷碼唔可以空")
    @Pattern(regexp = "^\\d{4}-\\d{3}$",
             message = "核銷碼格式：手機後4碼-訂單末3碼，例如 5678-023")
    private String method;
}
