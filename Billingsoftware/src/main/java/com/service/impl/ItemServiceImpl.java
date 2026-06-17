package com.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.entity.CategoryEntity;
import com.entity.ItemEntity;
import com.io.ItemRequest;
import com.io.ItemResponse;
import com.repository.CategoryRepository;
import com.repository.ItemRepository;
import com.repository.UserRepository;
import com.service.FileUploadService;
import com.service.ItemService;
import com.entity.UserEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.service.ItemService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final FileUploadService fileUploadService;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    private UserEntity getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Override
    public ItemResponse add(ItemRequest request, MultipartFile file) {

        String imgUrl = fileUploadService.UploadFile(file);

        UserEntity currentUser = getLoggedInUser();

        CategoryEntity category = categoryRepository
                .findByCategoryIdAndUserId(request.getCategoryId(), currentUser.getTenantId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        ItemEntity item = ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .gstPercentage(request.getGstPercentage())
                .stockQuantity(request.getStockQuantity())
                .category(category)
                .imgUrl(imgUrl)
                .userId(currentUser.getTenantId())
                .build();

        item = itemRepository.save(item);

        return ItemResponse.builder()
                .itemId(item.getItemId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .gstPercentage(item.getGstPercentage())
                .stockQuantity(item.getStockQuantity())
                .categoryId(category.getCategoryId())
                .categoryName(category.getName())
                .imgUrl(item.getImgUrl())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    @Override
    public List<ItemResponse> fetchItems() {
        UserEntity currentUser = getLoggedInUser();
        return itemRepository.findByUserId(currentUser.getTenantId())
                .stream()
                .map(item -> ItemResponse.builder()
                        .itemId(item.getItemId())
                        .name(item.getName())
                        .description(item.getDescription())
                        .price(item.getPrice())
                        .gstPercentage(item.getGstPercentage())
                        .stockQuantity(item.getStockQuantity())
                        .categoryId(item.getCategory().getCategoryId())
                        .categoryName(item.getCategory().getName())
                        .imgUrl(item.getImgUrl())
                        .createdAt(item.getCreatedAt())
                        .updatedAt(item.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteItem(String itemId) {
        UserEntity currentUser = getLoggedInUser();

        ItemEntity item = itemRepository.findByItemIdAndUserId(itemId, currentUser.getTenantId())
                .orElseThrow(() -> new RuntimeException("Item not found"));

        fileUploadService.deleteFile(item.getImgUrl());

        itemRepository.delete(item);
    }

    @Override
    public ItemResponse edit(String itemId, ItemRequest request, MultipartFile file) {
        UserEntity currentUser = getLoggedInUser();

        ItemEntity item = itemRepository.findByItemIdAndUserId(itemId, currentUser.getTenantId())
                .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));

        CategoryEntity category = categoryRepository.findByCategoryIdAndUserId(request.getCategoryId(), currentUser.getTenantId())
                .orElseThrow(() -> new RuntimeException("Category not found: " + request.getCategoryId()));

        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setGstPercentage(request.getGstPercentage());
        item.setStockQuantity(request.getStockQuantity());
        item.setCategory(category);

        if (file != null && !file.isEmpty()) {
            fileUploadService.deleteFile(item.getImgUrl());
            String newImgUrl = fileUploadService.UploadFile(file);
            item.setImgUrl(newImgUrl);
        }

        item = itemRepository.save(item);

        return ItemResponse.builder()
                .itemId(item.getItemId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .gstPercentage(item.getGstPercentage())
                .stockQuantity(item.getStockQuantity())
                .categoryId(category.getCategoryId())
                .categoryName(category.getName())
                .imgUrl(item.getImgUrl())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
