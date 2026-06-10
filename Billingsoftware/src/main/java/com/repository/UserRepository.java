package com.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long>{

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByVerificationToken(String verificationToken);

    Optional<UserEntity> findByUserId(String userId);

    List<UserEntity> findByShopOwnerId(String shopOwnerId);

    Long countByRole(String role);
}