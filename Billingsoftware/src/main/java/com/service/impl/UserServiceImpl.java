package com.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.entity.UserEntity;
import com.io.UserRequest;
import com.io.UserResponse;
import com.repository.UserRepository;
import com.service.UserService;
import com.service.EmailService;
import com.service.FileUploadService;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final FileUploadService fileUploadService;
	private final EmailService emailService;

	@Override
	public UserResponse createUser(UserRequest request, MultipartFile file, MultipartFile profilePhoto) {
		String imgUrl = null;
		if (file != null && !file.isEmpty()) {
			imgUrl = fileUploadService.UploadFile(file);
		}

		String profilePhotoUrl = null;
		if (profilePhoto != null && !profilePhoto.isEmpty()) {
			profilePhotoUrl = fileUploadService.UploadFile(profilePhoto);
		}
		
		UserEntity newUser = convertToEntity(request);
		newUser.setShopLogoUrl(imgUrl);
		newUser.setProfilePhotoUrl(profilePhotoUrl);
		
		// Determine hierarchy
		if (SecurityContextHolder.getContext().getAuthentication() != null &&
				SecurityContextHolder.getContext().getAuthentication().isAuthenticated() &&
				!SecurityContextHolder.getContext().getAuthentication().getName().equals("anonymousUser")) {
			String loggedInEmail = SecurityContextHolder.getContext().getAuthentication().getName();
			userRepository.findByEmail(loggedInEmail).ifPresent(creator -> {
				if ("ROLE_SHOPOWNER".equals(creator.getRole())) {
					newUser.setRole("ROLE_EMPLOYEE");
					newUser.setShopOwnerId(creator.getUserId());
					newUser.setShopId(creator.getShopId());
					newUser.setAccountStatus("APPROVED");
				} else if ("ROLE_SUPERADMIN".equals(creator.getRole())) {
					newUser.setRole("ROLE_SHOPOWNER");
					newUser.setShopId(UUID.randomUUID().toString());
					newUser.setAccountStatus("APPROVED");
				}
			});
		}

		String token = UUID.randomUUID().toString();
		newUser.setVerificationToken(token);
		newUser.setVerified(false);
		
		UserEntity savedUser = userRepository.save(newUser);
		
		emailService.sendVerificationEmail(savedUser.getEmail(), token);
		
		return convertToResponse(savedUser);
	}

	private UserResponse convertToResponse(UserEntity newUser) {
		return UserResponse.builder()
				.name(newUser.getName())
				.email(newUser.getEmail())
				.userId(newUser.getUserId())
				.createdAt(newUser.getCreatedAt())
				.updatedAt(newUser.getUpdatedAt())
				.role(newUser.getRole())
				.shopName(newUser.getShopName())
				.shopAddress(newUser.getShopAddress())
				.shopWebsite(newUser.getShopWebsite())
				.shopMobile(newUser.getShopMobile())
				.shopEmail(newUser.getShopEmail())
				.shopLogoUrl(newUser.getShopLogoUrl())
				.profilePhotoUrl(newUser.getProfilePhotoUrl())
				.gstNumber(newUser.getGstNumber())
				.whatsappEnabled(newUser.isWhatsappEnabled())
				.whatsappAutoSend(newUser.isWhatsappAutoSend())
				.businessType(newUser.getBusinessType())
				.loginProvider(newUser.getLoginProvider())
				.accountStatus(newUser.getAccountStatus())
				.shopId(newUser.getShopId())
				.approvedBy(newUser.getApprovedBy())
				.approvedDate(newUser.getApprovedDate())
				.build();
	}

	private UserEntity convertToEntity(UserRequest request) {
		return UserEntity.builder()
			.userId(UUID.randomUUID().toString())
			.email(request.getEmail())
			.password(request.getPassword() != null ? passwordEncoder.encode(request.getPassword()) : null)
			.role(request.getRole() != null ? request.getRole().toUpperCase() : null)
			.name(request.getName())
			.shopName(request.getShopName())
			.shopAddress(request.getShopAddress())
			.shopWebsite(request.getShopWebsite())
			.shopMobile(request.getShopMobile())
			.shopEmail(request.getShopEmail())
			.profilePhotoUrl(request.getProfilePhotoUrl())
			.gstNumber(request.getGstNumber())
			.whatsappEnabled(request.isWhatsappEnabled())
			.whatsappAutoSend(request.isWhatsappAutoSend())
			.businessType(request.getBusinessType())
			.loginProvider(request.getLoginProvider())
			.accountStatus(request.getAccountStatus())
			.shopId(request.getShopId())
			.build();
	}

	@Override
	public String getUserRole(String email) {
		 UserEntity existingUser = userRepository.findByEmail(email)
					.orElseThrow(() -> new UsernameNotFoundException("User not found for the email: "+email));
		return existingUser.getRole();
	}

	@Override
	public UserResponse getUserByEmail(String email) {
		UserEntity existingUser = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found for the email: "+email));
		return convertToResponse(existingUser);
	}

	@Override
	public List<UserResponse> readUsers() {
		if (SecurityContextHolder.getContext().getAuthentication() != null) {
			String email = SecurityContextHolder.getContext().getAuthentication().getName();
			UserEntity loggedInUser = userRepository.findByEmail(email).orElse(null);
			if (loggedInUser != null && "ROLE_SHOPOWNER".equals(loggedInUser.getRole())) {
				// Shop owners only see their employees
				return userRepository.findByShopOwnerId(loggedInUser.getUserId())
						.stream()
						.map(this::convertToResponse)
						.collect(Collectors.toList());
			}
		}

		// Super Admin sees everyone
		return userRepository.findAll()
				.stream()
				.map(this::convertToResponse)
				.collect(Collectors.toList());
	}

	@Override
	public void deleteUser(String id) {

	    UserEntity existingUser = userRepository.findByUserId(id)
	            .orElseThrow(() ->
	                    new UsernameNotFoundException("User not found"));

	    userRepository.delete(existingUser);
	}

	@Override
	public UserResponse editUser(String id, UserRequest request, MultipartFile file, MultipartFile profilePhoto) {
		UserEntity existingUser = userRepository.findByUserId(id)
				.orElseThrow(() -> new UsernameNotFoundException("User not found for the id: " + id));

		existingUser.setName(request.getName());
		existingUser.setEmail(request.getEmail());
		if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
			existingUser.setPassword(passwordEncoder.encode(request.getPassword()));
		}

		if (request.getRole() != null && !request.getRole().isEmpty()) {
			existingUser.setRole(request.getRole().toUpperCase());
		}

		existingUser.setShopName(request.getShopName());
		existingUser.setShopAddress(request.getShopAddress());
		existingUser.setShopWebsite(request.getShopWebsite());
		existingUser.setShopMobile(request.getShopMobile());
		existingUser.setShopEmail(request.getShopEmail());
		existingUser.setGstNumber(request.getGstNumber());
		existingUser.setWhatsappAutoSend(request.isWhatsappAutoSend());
		existingUser.setBusinessType(request.getBusinessType());
		existingUser.setAccountStatus(request.getAccountStatus());
		existingUser.setLoginProvider(request.getLoginProvider());
		existingUser.setShopId(request.getShopId());

		if (file != null && !file.isEmpty()) {
			String imgUrl = fileUploadService.UploadFile(file);
			existingUser.setShopLogoUrl(imgUrl);
		}

		if (profilePhoto != null && !profilePhoto.isEmpty()) {
			String imgUrl = fileUploadService.UploadFile(profilePhoto);
			existingUser.setProfilePhotoUrl(imgUrl);
		}

		UserEntity savedUser = userRepository.save(existingUser);
		return convertToResponse(savedUser);
	}

	@Override
	public UserResponse toggleWhatsApp(String id, boolean enabled) {
		UserEntity existingUser = userRepository.findByUserId(id)
				.orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found for the id: " + id));
		existingUser.setWhatsappEnabled(enabled);
		UserEntity savedUser = userRepository.save(existingUser);
		return convertToResponse(savedUser);
	}
}
