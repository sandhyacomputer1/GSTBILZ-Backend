package com.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.entity.CategoryEntity;
import com.io.CategoryRequest;
import com.io.CategoryResponse;
import com.repository.CategoryRepository;
import com.repository.ItemRepository;
import com.repository.UserRepository;
import com.service.CategoryService;
import com.service.FileUploadService;
import com.entity.UserEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final FileUploadService fileUploadService;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    private UserEntity getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Override
    public CategoryResponse add(CategoryRequest request, MultipartFile file) {

        String imgUrl = null;

        if (file != null && !file.isEmpty()) {
            imgUrl = fileUploadService.UploadFile(file);
        }

        UserEntity currentUser = getLoggedInUser();

        CategoryEntity entity = CategoryEntity.builder()
                .categoryId(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .bgColor(request.getBgColor())
                .imgUrl(imgUrl)
                .userId(currentUser.getTenantId())
                .build();

        entity = categoryRepository.save(entity);

        System.out.println("Saved Image URL: " + entity.getImgUrl());

        return convertResponse(entity);
    }

    @Override
    public List<CategoryResponse> read() {
        UserEntity currentUser = getLoggedInUser();
        return categoryRepository.findByUserId(currentUser.getTenantId())
                .stream()
                .map(this::convertResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String categoryId) {
        UserEntity currentUser = getLoggedInUser();

        CategoryEntity existingCategory = categoryRepository.findByCategoryIdAndUserId(categoryId, currentUser.getTenantId())
                .orElseThrow(() -> new RuntimeException("Category Not Found : " + categoryId));

        Integer itemsCount = itemRepository.countByCategory_IdAndUserId(existingCategory.getId(), currentUser.getTenantId());
        if (itemsCount != null && itemsCount > 0) {
            throw new RuntimeException("Cannot delete category! There are " + itemsCount + " products currently in this category. Please delete or reassign them first.");
        }

        fileUploadService.deleteFile(existingCategory.getImgUrl());

        categoryRepository.delete(existingCategory);
    }

    @Override
    public CategoryResponse edit(String categoryId, CategoryRequest request, MultipartFile file) {
        UserEntity currentUser = getLoggedInUser();

        CategoryEntity existingCategory = categoryRepository.findByCategoryIdAndUserId(categoryId, currentUser.getTenantId())
                .orElseThrow(() -> new RuntimeException("Category Not Found : " + categoryId));

        existingCategory.setName(request.getName());
        existingCategory.setBgColor(request.getBgColor());

        if (file != null && !file.isEmpty()) {
            // delete old image
            fileUploadService.deleteFile(existingCategory.getImgUrl());
            // upload new image
            String newImgUrl = fileUploadService.UploadFile(file);
            existingCategory.setImgUrl(newImgUrl);
        }

        existingCategory = categoryRepository.save(existingCategory);

        return CategoryResponse.builder()
                .categoryId(existingCategory.getCategoryId())
                .name(existingCategory.getName())
                .imgUrl(existingCategory.getImgUrl())
                .bgColor(existingCategory.getBgColor())
                .items(itemRepository.countByCategory_IdAndUserId(existingCategory.getId(), currentUser.getTenantId()))
                .build();
    }

    private CategoryResponse convertResponse(CategoryEntity newCategory) {
        UserEntity currentUser = getLoggedInUser();

        Integer itemsCount = itemRepository.countByCategory_IdAndUserId(newCategory.getId(), currentUser.getTenantId()); // ✅ FIXED

        return CategoryResponse.builder()
                .categoryId(newCategory.getCategoryId())
                .id(newCategory.getId())
                .name(newCategory.getName())
                .description(newCategory.getDescription())
                .bgColor(newCategory.getBgColor())
                .imgUrl(newCategory.getImgUrl())
                .createdAt(newCategory.getCreatedAt())
                .updateAt(newCategory.getUpdatedAt())
                .items(itemsCount)
                .build();
    }
}
