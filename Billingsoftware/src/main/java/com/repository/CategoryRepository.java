package com.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.entity.CategoryEntity;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    Optional<CategoryEntity> findByCategoryId(String categoryId);
    Optional<CategoryEntity> findByCategoryIdAndUserId(String categoryId, String userId);

    Optional<CategoryEntity> findByNameIgnoreCase(String name);
    java.util.List<CategoryEntity> findByNameIgnoreCaseAndUserId(String name, String userId);

    java.util.List<CategoryEntity> findByUserId(String userId);
}