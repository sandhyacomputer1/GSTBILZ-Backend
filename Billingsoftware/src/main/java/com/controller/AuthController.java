package com.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.entity.UserEntity;
import com.repository.UserRepository;

import com.io.AuthRequest;
import com.io.AuthResponse;
import com.service.UserService;
import com.service.impl.AppUserDetailsService;
import com.util.JwtUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {
	
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	
	private final AppUserDetailsService appUserDetailsService;
	private final UserService userService;
	private final UserRepository userRepository;
	private final com.service.EmailService emailService;
	
	private final JwtUtil jwtUtil;
	
	@PostMapping("/login")
	public AuthResponse login(@RequestBody AuthRequest request) throws Exception {
		UserEntity user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or password is incorrect"));
				
		if (!user.isVerified()) {
			// Backwards compatibility: Existing users (like admin) created before this feature
			// will have a null verificationToken. We auto-verify them.
			if (user.getVerificationToken() == null || "ROLE_SUPERADMIN".equals(user.getRole()) || "ROLE_SHOPOWNER".equals(user.getRole()) || "ROLE_EMPLOYEE".equals(user.getRole())) {
				user.setVerified(true);
				userRepository.save(user);
			} else {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your email address before logging in.");
			}
		}

		if (user.getAccountStatus() != null) {
			if ("PENDING".equals(user.getAccountStatus())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your shop account is pending approval by the Super Admin.");
			} else if ("REJECTED".equals(user.getAccountStatus())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your shop account registration has been rejected.");
			} else if ("DISABLED".equals(user.getAccountStatus())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your shop account has been disabled. Please contact support.");
			}
		}

		authenticate(request.getEmail(), request.getPassword());
		final UserDetails userDetails = appUserDetailsService.loadUserByUsername(request.getEmail());
		final String JwtToken = jwtUtil.generateToken(userDetails);
		String role = user.getRole();
		
		// TODO: fetch the role form repository
		return new AuthResponse(request.getEmail(), JwtToken, role);
	
	}
	
	@PostMapping("/register-shop")
	public com.io.UserResponse registerShop(@RequestBody com.io.UserRequest request) {
		if (userRepository.findByEmail(request.getEmail()).isPresent()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered.");
		}
		UserEntity shopOwner = UserEntity.builder()
				.userId(java.util.UUID.randomUUID().toString())
				.email(request.getEmail())
				.password(passwordEncoder.encode(request.getPassword()))
				.role("ROLE_SHOPOWNER")
				.name(request.getName())
				.shopName(request.getShopName())
				.shopAddress(request.getShopAddress())
				.shopMobile(request.getShopMobile())
				.gstNumber(request.getGstNumber())
				.businessType(request.getBusinessType())
				.accountStatus("PENDING")
				.loginProvider("EMAIL")
				.shopId(java.util.UUID.randomUUID().toString())
				.isVerified(true)
				.build();
		UserEntity saved = userRepository.save(shopOwner);
		emailService.sendRegistrationCompletedEmail(saved.getEmail(), saved.getShopName());
		
		return com.io.UserResponse.builder()
				.userId(saved.getUserId())
				.name(saved.getName())
				.email(saved.getEmail())
				.createdAt(saved.getCreatedAt())
				.updatedAt(saved.getUpdatedAt())
				.role(saved.getRole())
				.shopName(saved.getShopName())
				.shopAddress(saved.getShopAddress())
				.shopMobile(saved.getShopMobile())
				.gstNumber(saved.getGstNumber())
				.businessType(saved.getBusinessType())
				.loginProvider(saved.getLoginProvider())
				.accountStatus(saved.getAccountStatus())
				.shopId(saved.getShopId())
				.build();
	}

	@PostMapping("/login/google")
	public AuthResponse loginGoogle(@RequestBody Map<String, String> requestPayload) throws Exception {
		String idToken = requestPayload.get("idToken");
		if (idToken == null || idToken.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token is required.");
		}
		
		Map<String, Object> tokenInfo;
		try {
			org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
			String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
			tokenInfo = restTemplate.getForObject(url, Map.class);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token or verification failed.");
		}
		
		if (tokenInfo == null || tokenInfo.containsKey("error_description")) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token.");
		}
		
		String email = (String) tokenInfo.get("email");
		String name = (String) tokenInfo.get("name");
		if (email == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email not returned from Google token.");
		}
		
		UserEntity user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			// Auto register new shop owner
			user = UserEntity.builder()
					.userId(java.util.UUID.randomUUID().toString())
					.email(email)
					.role("ROLE_SHOPOWNER")
					.name(name)
					.shopName(name + "'s Shop")
					.accountStatus("PENDING")
					.loginProvider("GOOGLE")
					.shopId(java.util.UUID.randomUUID().toString())
					.isVerified(true)
					.build();
			user = userRepository.save(user);
			emailService.sendRegistrationCompletedEmail(email, user.getShopName());
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your shop account is pending approval by the Super Admin.");
		}
		
		// If user exists, check status
		if (user.getAccountStatus() != null) {
			if ("PENDING".equals(user.getAccountStatus())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your shop account is pending approval by the Super Admin.");
			} else if ("REJECTED".equals(user.getAccountStatus())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your shop account registration has been rejected.");
			} else if ("DISABLED".equals(user.getAccountStatus())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your shop account has been disabled. Please contact support.");
			}
		}
		
		final UserDetails userDetails = appUserDetailsService.loadUserByUsername(email);
		final String JwtToken = jwtUtil.generateToken(userDetails);
		return new AuthResponse(email, JwtToken, user.getRole());
	}
	
	private void authenticate(String email, String password) {
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
		}catch (DisabledException e) {
		    throw new ResponseStatusException(
		            HttpStatus.UNAUTHORIZED,
		            "User disabled");
		
		}catch (BadCredentialsException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or password is incorrect");
		}
		
	}

	@PostMapping("/encode")
	public String encodePassword(@RequestBody Map<String, String> request) {
		return passwordEncoder.encode(request.get("password"));
	}

	@GetMapping("/verify-email")
	public String verifyEmail(@RequestParam("token") String token) {
		UserEntity user = userRepository.findByVerificationToken(token)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token"));

		if (user.isVerified()) {
			return "Email is already verified. You can login.";
		}

		user.setVerified(true);
		userRepository.save(user);

		return "Email successfully verified! You can now login.";
	}

	@PostMapping("/forgot-password")
	public void forgotPassword(@RequestBody Map<String, String> request) {
		String email = request.get("email");
		if (email == null || email.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
		}
		UserEntity user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User with this email does not exist."));

		String otp = String.format("%06d", new java.util.Random().nextInt(999999));
		user.setOtpCode(otp);
		user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(5));
		userRepository.save(user);

		emailService.sendOtpEmail(email, otp);
	}

	@PostMapping("/reset-password")
	public void resetPassword(@RequestBody Map<String, String> request) {
		String email = request.get("email");
		String otp = request.get("otp");
		String newPassword = request.get("newPassword");

		if (email == null || otp == null || newPassword == null || email.trim().isEmpty() || otp.trim().isEmpty() || newPassword.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All fields (email, otp, newPassword) are required.");
		}

		UserEntity user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User with this email does not exist."));

		if (user.getOtpCode() == null || !user.getOtpCode().equals(otp) || user.getOtpExpiry() == null || java.time.LocalDateTime.now().isAfter(user.getOtpExpiry())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP.");
		}

		user.setPassword(passwordEncoder.encode(newPassword));
		user.setOtpCode(null);
		user.setOtpExpiry(null);
		userRepository.save(user);
	}
}
