package com.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.io.CategoryRequest;
import com.io.CategoryResponse;

public interface CategoryService {
	
	CategoryResponse add(CategoryRequest request, MultipartFile file);
	
	List<CategoryResponse> read();
	
	void delete(String categoryId);

    CategoryResponse edit(String categoryId, CategoryRequest request, MultipartFile file);
}
