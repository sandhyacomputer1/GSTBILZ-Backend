package com.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.entity.InvoiceEntity;

@Repository
public interface InvoiceEntityRepository extends JpaRepository<InvoiceEntity, Long> {
    Optional<InvoiceEntity> findByInvoiceNumber(String invoiceNumber);
    Optional<InvoiceEntity> findByInvoiceNumberAndUserId(String invoiceNumber, String userId);
    Optional<InvoiceEntity> findByOrderId(String orderId);
}
