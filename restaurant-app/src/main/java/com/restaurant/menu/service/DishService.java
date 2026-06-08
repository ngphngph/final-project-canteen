package com.restaurant.menu.service;

import com.restaurant.menu.dto.*;
import com.restaurant.menu.entity.Dish;
import com.restaurant.menu.repository.DishRepository;
import com.restaurant.menu.util.StockStatusUtil;
import com.restaurant.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DishService {

    private final DishRepository dishRepository;
    private final ImageService imageService;
    private final OrderWindowService orderWindowService;

    @Caching(evict = {
        @CacheEvict(value = "dishes-today", allEntries = true),
        @CacheEvict(value = "menus-today",  allEntries = true)
    })
    public DishResponse create(DishRequest request, MultipartFile image) throws Exception {
        if (image == null || image.isEmpty()) throw new IllegalArgumentException("Dish image is required");
        Dish dish = new Dish();
        applyRequest(dish, request);
        dish.setImageUrl(imageService.uploadImage(image, "dish"));
        dish.setStatus(StockStatusUtil.syncStatus(dish.getBalance()));
        return toResponse(dishRepository.save(dish));
    }

    @Caching(evict = {
        @CacheEvict(value = "dish-by-id",  key = "#id"),
        @CacheEvict(value = "dishes-today", allEntries = true),
        @CacheEvict(value = "menus-today",  allEntries = true)
    })
    public DishResponse update(Long id, DishRequest request, MultipartFile image) throws Exception {
        Dish dish = findEntity(id);
        applyRequest(dish, request);
        if (image != null && !image.isEmpty()) {
            dish.setImageUrl(imageService.uploadImage(image, "dish"));
        }
        dish.setStatus(StockStatusUtil.syncStatus(dish.getBalance()));
        return toResponse(dishRepository.save(dish));
    }

    @Caching(evict = {
        @CacheEvict(value = "dish-by-id",  key = "#id"),
        @CacheEvict(value = "dishes-today", allEntries = true),
        @CacheEvict(value = "menus-today",  allEntries = true)
    })
    public DishResponse updateBalance(Long id, BalanceUpdateRequest request) {
        Dish dish = findEntity(id);
        dish.setBalance(request.getBalance());
        dish.setStatus(StockStatusUtil.syncStatus(dish.getBalance()));
        return toResponse(dishRepository.save(dish));
    }

    @Caching(evict = {
        @CacheEvict(value = "dish-by-id",  key = "#id"),
        @CacheEvict(value = "dishes-today", allEntries = true),
        @CacheEvict(value = "menus-today",  allEntries = true)
    })
    public DishResponse updateBasicInfo(Long id, DishBasicUpdateRequest request) {
        Dish dish = findEntity(id);
        dish.setName(request.getName());
        dish.setPrice(request.getPrice());
        return toResponse(dishRepository.save(dish));
    }

    @Caching(evict = {
        @CacheEvict(value = "dish-by-id",  key = "#id"),
        @CacheEvict(value = "dishes-today", allEntries = true),
        @CacheEvict(value = "menus-today",  allEntries = true)
    })
    public DishResponse updateSpecialRequestOptions(Long id, SpecialRequestOptionsUpdateRequest request) {
        Dish dish = findEntity(id);
        dish.setSpecialRequestOptions(request.getSpecialRequestOptions());
        return toResponse(dishRepository.save(dish));
    }

    @Transactional(readOnly = true)
    public DishBalanceResponse getBalance(Long id) {
        Dish dish = findEntity(id);
        return new DishBalanceResponse(dish.getId(), dish.getBalance(), dish.getStatus());
    }

    @Caching(evict = {
        @CacheEvict(value = "dish-by-id",  key = "#id"),
        @CacheEvict(value = "dishes-today", allEntries = true),
        @CacheEvict(value = "menus-today",  allEntries = true)
    })
    public DishResponse uploadImage(Long id, MultipartFile image) throws Exception {
        Dish dish = findEntity(id);
        dish.setImageUrl(imageService.uploadImage(image, "dish"));
        return toResponse(dishRepository.save(dish));
    }

    @Caching(evict = {
        @CacheEvict(value = "dish-by-id",  key = "#id"),
        @CacheEvict(value = "dishes-today", allEntries = true),
        @CacheEvict(value = "menus-today",  allEntries = true)
    })
    public void delete(Long id) {
        if (!dishRepository.existsById(id)) throw new ResourceNotFoundException("Dish not found: " + id);
        dishRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "dish-by-id", key = "#id")
    public DishResponse getById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<DishResponse> listByDate(LocalDate date) {
        return dishRepository.findByMenuDate(date).stream().map(DishService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable("dishes-today")
    public List<DishResponse> getTodayForClient() {
        orderWindowService.assertOpen();
        LocalDate today = LocalDate.now(orderWindowService.getZoneId());
        return dishRepository.findTodayAvailable(today).stream().map(DishService::toResponse).toList();
    }

    private Dish findEntity(Long id) {
        return dishRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dish not found: " + id));
    }

    private void applyRequest(Dish dish, DishRequest request) {
        dish.setName(request.getName());
        dish.setCode(request.getCode());
        dish.setPrice(request.getPrice());
        dish.setInitialStock(request.getInitialStock());
        dish.setPreparationTime(request.getPreparationTime());
        dish.setMenuDate(request.getMenuDate() != null ? request.getMenuDate() : LocalDate.now());
        dish.setSpecialRequestOptions(request.getSpecialRequestOptions());
        if (dish.getBalance() == null) {
            dish.setBalance(request.getInitialStock());
        }
    }

    static DishResponse toResponse(Dish dish) {
        return DishResponse.builder()
                .id(dish.getId())
                .name(dish.getName())
                .code(dish.getCode())
                .price(dish.getPrice())
                .imageUrl(dish.getImageUrl())
                .specialRequestOptions(dish.getSpecialRequestOptions())
                .initialStock(dish.getInitialStock())
                .balance(dish.getBalance())
                .preparationTime(dish.getPreparationTime())
                .menuDate(dish.getMenuDate())
                .status(dish.getStatus())
                .build();
    }
}
