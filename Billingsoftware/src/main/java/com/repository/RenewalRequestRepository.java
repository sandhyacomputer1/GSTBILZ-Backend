package com.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.entity.RenewalRequestEntity;

public interface RenewalRequestRepository extends JpaRepository<RenewalRequestEntity, Long> {
    List<RenewalRequestEntity> findByStatusOrderByRequestDateDesc(String status);
    Optional<RenewalRequestEntity> findFirstByShopOwnerIdAndStatus(String shopOwnerId, String status);
}
