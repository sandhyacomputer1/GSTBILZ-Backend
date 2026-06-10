package com.service;

import java.util.List;

import com.io.UserRequest;
import com.io.UserResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
	
	UserResponse createUser(UserRequest request, MultipartFile file, MultipartFile profilePhoto);
	String getUserRole(String email);
	UserResponse getUserByEmail(String email);
	List<UserResponse> readUsers();
	void deleteUser(String id);
	UserResponse editUser(String id, UserRequest request, MultipartFile file, MultipartFile profilePhoto);
	UserResponse toggleWhatsApp(String id, boolean enabled);
}
