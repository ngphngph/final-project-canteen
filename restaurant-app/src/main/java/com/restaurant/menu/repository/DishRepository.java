package com.restaurant.menu.repository;

import com.restaurant.menu.entity.Dish;
import com.restaurant.menu.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findByMenuDate(LocalDate menuDate);
    List<Dish> findByMenuDateAndStatus(LocalDate menuDate, Status status);
    Optional<Dish> findByMenuDateAndCode(LocalDate menuDate, String code);

    @Query("SELECT d FROM Dish d WHERE d.menuDate = :date AND d.balance > 0 AND d.status = 'ON_LIST'")
    List<Dish> findTodayAvailable(@Param("date") LocalDate date);
}
