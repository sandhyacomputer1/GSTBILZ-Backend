package com.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_renewal_requests")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RenewalRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shopOwnerId;
    private String shopName;
    private String ownerName;
    private String email;
    private String mobile;
    private String subscriptionStatus;
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime requestDate;

    private String status; // PENDING, RESOLVED
}
