package com.io;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserResponse {
	
	private String userId;
	private String name;
	private String email;
	private Timestamp createdAt;
	private Timestamp updatedAt;
	private String role;
	private String shopName;
	private String shopAddress;
	private String shopWebsite;
	private String shopMobile;
	private String shopEmail;
	private String shopLogoUrl;
	private String profilePhotoUrl;
	private String gstNumber;
	private boolean whatsappEnabled;
	private boolean whatsappAutoSend;
	private String businessType;
	private String loginProvider;
	private String accountStatus;
	private String shopId;
	private String approvedBy;
	private java.time.LocalDateTime approvedDate;

}
