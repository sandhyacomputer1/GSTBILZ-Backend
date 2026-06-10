package com.entity;

import java.sql.Timestamp;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "tbl_users")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String userId;
	private String email;
	private String password;
	private String role;
	private String name;
	private String shopName;
	private String shopAddress;
	private String shopWebsite;
	private String shopMobile;
	private String shopEmail;
	private String shopLogoUrl;
	private String profilePhotoUrl;
	private String shopOwnerId; // Null for Super Admin/Shop Owner. Set to Shop Owner's userId for Employees.
	private String gstNumber;

	private String accountStatus; // PENDING, APPROVED, REJECTED, DISABLED
	private String loginProvider; // EMAIL, GOOGLE
	private String approvedBy;
	private java.time.LocalDateTime approvedDate;
	private String shopId;
	private String businessType;
	
	@Builder.Default
	private boolean whatsappEnabled = false;

	@Builder.Default
	private boolean whatsappAutoSend = false;
	
	@Builder.Default
	private boolean isVerified = false;
	private String verificationToken;
	private String otpCode;
	private java.time.LocalDateTime otpExpiry;

	@CreationTimestamp
	@Column(updatable = false)
	private Timestamp createdAt;
	@UpdateTimestamp
	private Timestamp updatedAt;
	
	public String getTenantId() {
		if (shopId != null) {
			return shopId;
		}
		return "ROLE_EMPLOYEE".equals(role) && shopOwnerId != null ? shopOwnerId : userId;
	}

}
