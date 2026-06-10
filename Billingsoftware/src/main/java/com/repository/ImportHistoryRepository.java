package com.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.entity.ImportHistoryEntity;

public interface ImportHistoryRepository extends JpaRepository<ImportHistoryEntity, Long> {

    List<ImportHistoryEntity> findAllByOrderByCreatedAtDesc();
    List<ImportHistoryEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
