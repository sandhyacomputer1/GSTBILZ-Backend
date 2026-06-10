package com.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.entity.OrderItemEntity;

public interface OrderItemEntityRepository extends JpaRepository<OrderItemEntity, Long>{

}
