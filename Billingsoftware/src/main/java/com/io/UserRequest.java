package com.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRequest {
	
	private String name;
	private String email;
	private String password;
	private String role;
	private String shopName;
	private String shopAddress;
	private String shopWebsite;
	private String shopMobile;
	private String shopEmail;
	private String profilePhotoUrl;
	private String gstNumber;
	private boolean whatsappEnabled;
	private boolean whatsappAutoSend;
	private String businessType;
	private String loginProvider;
	private String accountStatus;
	private String shopId;
}
