package com.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.entity.UserEntity;
import com.io.UserResponse;
import com.repository.UserRepository;
import com.service.EmailService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/shop-owners")
@RequiredArgsConstructor
public class SuperAdminController {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @GetMapping
    public List<UserResponse> getShopOwners() {
        return userRepository.findAll().stream()
                .filter(user -> "ROLE_SHOPOWNER".equals(user.getRole()))
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @PutMapping("/{userId}/approve")
    public UserResponse approveShopOwner(@PathVariable("userId") String userId) {
        UserEntity user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop owner not found."));

        if (!"ROLE_SHOPOWNER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a shop owner.");
        }

        String loggedInEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        user.setAccountStatus("APPROVED");
        user.setApprovedBy(loggedInEmail);
        user.setApprovedDate(LocalDateTime.now());
        if (user.getShopId() == null) {
            user.setShopId(UUID.randomUUID().toString());
        }

        UserEntity saved = userRepository.save(user);
        emailService.sendAccountApprovedEmail(saved.getEmail(), saved.getName());

        return convertToResponse(saved);
    }

    @PutMapping("/{userId}/reject")
    public UserResponse rejectShopOwner(@PathVariable("userId") String userId) {
        UserEntity user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop owner not found."));

        if (!"ROLE_SHOPOWNER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a shop owner.");
        }

        user.setAccountStatus("REJECTED");
        UserEntity saved = userRepository.save(user);
        emailService.sendAccountRejectedEmail(saved.getEmail(), saved.getName());

        return convertToResponse(saved);
    }

    @PutMapping("/{userId}/disable")
    public UserResponse toggleDisableShopOwner(@PathVariable("userId") String userId) {
        UserEntity user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop owner not found."));

        if (!"ROLE_SHOPOWNER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a shop owner.");
        }

        if ("DISABLED".equals(user.getAccountStatus())) {
            user.setAccountStatus("APPROVED");
        } else {
            user.setAccountStatus("DISABLED");
        }

        UserEntity saved = userRepository.save(user);
        return convertToResponse(saved);
    }

    @PutMapping("/{userId}/toggle-whatsapp")
    public UserResponse toggleWhatsAppCapability(@PathVariable("userId") String userId) {
        UserEntity user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop owner not found."));

        if (!"ROLE_SHOPOWNER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a shop owner.");
        }

        user.setWhatsappEnabled(!user.isWhatsappEnabled());
        UserEntity saved = userRepository.save(user);
        return convertToResponse(saved);
    }

    private UserResponse convertToResponse(UserEntity user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .role(user.getRole())
                .shopName(user.getShopName())
                .shopAddress(user.getShopAddress())
                .shopWebsite(user.getShopWebsite())
                .shopMobile(user.getShopMobile())
                .shopEmail(user.getShopEmail())
                .shopLogoUrl(user.getShopLogoUrl())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .gstNumber(user.getGstNumber())
                .whatsappEnabled(user.isWhatsappEnabled())
                .whatsappAutoSend(user.isWhatsappAutoSend())
                .businessType(user.getBusinessType())
                .loginProvider(user.getLoginProvider())
                .accountStatus(user.getAccountStatus())
                .shopId(user.getShopId())
                .approvedBy(user.getApprovedBy())
                .approvedDate(user.getApprovedDate())
                .build();
    }
}
