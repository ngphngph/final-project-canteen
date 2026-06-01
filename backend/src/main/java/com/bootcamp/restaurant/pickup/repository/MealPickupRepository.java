package com.bootcamp.restaurant.pickup.repository;

import com.bootcamp.restaurant.pickup.entity.MealPickup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MealPickupRepository extends JpaRepository<MealPickup, Long> {

    Optional<MealPickup> findByItemId(Long itemId);

    Optional<MealPickup> findByItemIdAndMethod(Long itemId, String method);

    // 逾時未取：expected_time 已過 但 actual_time 仍係 NULL
    @Query("SELECT m FROM MealPickup m WHERE m.actualTime IS NULL AND m.expectedTime < :now")
    List<MealPickup> findOverduePickups(Instant now);

    List<MealPickup> findByAdminNotifiedFalse();
}
