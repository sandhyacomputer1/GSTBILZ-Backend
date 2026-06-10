package com.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.io.UserRequest;
import com.io.UserResponse;
import com.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class UserContoller {
	private final UserService userService;
	
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse registerUser(
			@RequestPart("user") String userString,
			@RequestPart(value = "file", required = false) MultipartFile file,
			@RequestPart(value = "profilePhoto", required = false) MultipartFile profilePhoto) {
		try {
			UserRequest request = new ObjectMapper().readValue(userString, UserRequest.class);
			return userService.createUser(request, file, profilePhoto);
		} catch (JsonProcessingException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exception occurred while parsing json: " + e.getMessage());
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create user "+e.getMessage());
		}
	}
	
	@GetMapping("/profile")
	public UserResponse getProfile() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();
		return userService.getUserByEmail(email);
	}
	
	@GetMapping("/users")
	public List<UserResponse> readUsers(){
		return userService.readUsers();
	}
	
	@DeleteMapping("/user/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteUser(@PathVariable String id) {
		try {
			userService.deleteUser(id);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		}
	}

	@PutMapping("/user/{id}")
	public UserResponse editUser(
			@PathVariable String id,
			@RequestPart("user") String userString,
			@RequestPart(value = "file", required = false) MultipartFile file,
			@RequestPart(value = "profilePhoto", required = false) MultipartFile profilePhoto) {
		try {
			UserRequest request = new ObjectMapper().readValue(userString, UserRequest.class);
			return userService.editUser(id, request, file, profilePhoto);
		} catch (JsonProcessingException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exception occurred while parsing json: " + e.getMessage());
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to edit user: " + e.getMessage());
		}
	}

	@PutMapping("/profile")
	public UserResponse updateProfile(
			@RequestPart("user") String userString,
			@RequestPart(value = "file", required = false) MultipartFile file,
			@RequestPart(value = "profilePhoto", required = false) MultipartFile profilePhoto) {
		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			String email = authentication.getName();
			UserResponse currentProfile = userService.getUserByEmail(email);
			
			UserRequest request = new ObjectMapper().readValue(userString, UserRequest.class);
			if (request.getRole() == null || request.getRole().isEmpty()) {
				request.setRole(currentProfile.getRole());
			}
			return userService.editUser(currentProfile.getUserId(), request, file, profilePhoto);
		} catch (JsonProcessingException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exception occurred while parsing json: " + e.getMessage());
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to update profile: " + e.getMessage());
		}
	}

	@PutMapping("/user/{id}/whatsapp")
	public UserResponse toggleWhatsApp(
			@PathVariable String id,
			@org.springframework.web.bind.annotation.RequestParam("enabled") boolean enabled) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean isSuperAdmin = authentication.getAuthorities().stream()
				.anyMatch(r -> r.getAuthority().equals("ROLE_SUPERADMIN"));
		if (!isSuperAdmin) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Super Admin can toggle WhatsApp settings");
		}
		return userService.toggleWhatsApp(id, enabled);
	}
}
