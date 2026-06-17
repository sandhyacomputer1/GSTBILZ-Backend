package com.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.CategoryRequest;
import com.io.CategoryResponse;
import com.service.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping("/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse addCategory(
            @RequestPart("category") String categoryString,
            @RequestParam("file") MultipartFile file) {

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            CategoryRequest request =
                    objectMapper.readValue(categoryString, CategoryRequest.class);

            return categoryService.add(request, file);

        } catch (JsonProcessingException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exception occurred while parsing json: " + ex.getMessage());
        }
    }

    @GetMapping("/categories")
    public List<CategoryResponse> fetchCategories() {
        return categoryService.read();
    }

    @DeleteMapping("/admin/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String categoryId) {

        try {
            categoryService.delete(categoryId);

        } catch (Exception e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage());
        }
    }

    @PutMapping("/admin/categories/{categoryId}")
    public CategoryResponse editCategory(
            @PathVariable String categoryId,
            @RequestPart("category") String categoryString,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            CategoryRequest request =
                    objectMapper.readValue(categoryString, CategoryRequest.class);

            return categoryService.edit(categoryId, request, file);

        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exception occurred while parsing json: " + ex.getMessage());
        }
    }
}