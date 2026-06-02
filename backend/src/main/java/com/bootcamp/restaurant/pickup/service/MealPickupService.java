package com.bootcamp.restaurant.pickup.service;

import com.bootcamp.restaurant.pickup.dto.PickupCreateRequest;
import com.bootcamp.restaurant.pickup.dto.PickupResponse;
import com.bootcamp.restaurant.pickup.dto.PickupVerifyRequest;
import com.bootcamp.restaurant.pickup.entity.MealPickup;
import com.bootcamp.restaurant.pickup.repository.MealPickupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealPickupService {

    private final MealPickupRepository repository;

    // ── 由同學4 OrderService 付款成功後呼叫 ──────────────────────────────────
    @Transactional
    public PickupResponse createPickup(PickupCreateRequest req) {
        if (repository.findByItemId(req.getItemId()).isPresent()) {
            throw new IllegalStateException("Item_ID " + req.getItemId() + " 已經有取餐記錄");
        }
        MealPickup pickup = new MealPickup();
        pickup.setItemId(req.getItemId());
        pickup.setExpectedTime(req.getExpectedTime());
        pickup.setMethod(req.getMethod());
        pickup.setAdminNotified(false);
        repository.save(pickup);
        return toResponse(pickup, "取餐記錄建立成功");
    }

    // ── Admin 現場核銷 ────────────────────────────────────────────────────────
    @Transactional
    public PickupResponse verifyPickup(PickupVerifyRequest req) {
        MealPickup pickup = repository.findByItemIdAndMethod(req.getItemId(), req.getMethod())
                .orElseThrow(() -> new IllegalArgumentException("核銷碼錯誤或找不到對應記錄"));
        if (pickup.getActualTime() != null) {
            throw new IllegalStateException("已經核銷過：" + pickup.getActualTime());
        }
        pickup.setActualTime(Instant.now());
        pickup.setAdminId(req.getAdminId());
        repository.save(pickup);
        return toResponse(pickup, "✅ 核銷成功");
    }

    // ── 查詢逾時未取餐 ────────────────────────────────────────────────────────
    public List<PickupResponse> getOverduePickups() {
        return repository.findOverduePickups(Instant.now())
                .stream().map(p -> toResponse(p, "⚠️ 逾時未取")).toList();
    }

    // ── 標記已通知 Admin ──────────────────────────────────────────────────────
    @Transactional
    public PickupResponse markAdminNotified(Long pickupId) {
        MealPickup pickup = repository.findById(pickupId)
                .orElseThrow(() -> new IllegalArgumentException("找不到 pickupId: " + pickupId));
        pickup.setAdminNotified(true);
        repository.save(pickup);
        return toResponse(pickup, "已標記通知 Admin");
    }

    // ── 查詢單一記錄 ──────────────────────────────────────────────────────────
    public PickupResponse getByItemId(Long itemId) {
        return toResponse(repository.findByItemId(itemId)
                .orElseThrow(() -> new IllegalArgumentException("找不到 itemId: " + itemId)), null);
    }

    // ── 查詢未通知 ────────────────────────────────────────────────────────────
    public List<PickupResponse> getUnnotified() {
        return repository.findByAdminNotifiedFalse()
                .stream().map(p -> toResponse(p, "未通知")).toList();
    }

    private PickupResponse toResponse(MealPickup p, String message) {
        return PickupResponse.builder()
                .pickupId(p.getPickupId()).itemId(p.getItemId()).adminId(p.getAdminId())
                .method(p.getMethod()).expectedTime(p.getExpectedTime())
                .actualTime(p.getActualTime()).adminNotified(p.getAdminNotified())
                .message(message).build();
    }
}
