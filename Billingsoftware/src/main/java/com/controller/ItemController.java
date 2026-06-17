package com.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.ItemRequest;
import com.io.ItemResponse;
import com.service.ItemService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PostMapping("/admin/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse addItem(
            @RequestPart("items") String request,
            @RequestParam("file") MultipartFile file) {

        try {
            ItemRequest itemRequest =
                    new ObjectMapper().readValue(request, ItemRequest.class);

            return itemService.add(itemRequest, file);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exception occurred while parsing json: " + ex.getMessage());
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ex.getMessage(), ex);
        }
    }

    @GetMapping("/items")
    public List<ItemResponse> readItems() {
        return itemService.fetchItems();
    }

    @DeleteMapping("/admin/items/{itemId}")
    public void delete(@PathVariable String itemId) {
        itemService.deleteItem(itemId);
    }

    @PutMapping("/admin/items/{itemId}")
    public ItemResponse editItem(
            @PathVariable String itemId,
            @RequestPart("items") String request,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            ItemRequest itemRequest =
                    new ObjectMapper().readValue(request, ItemRequest.class);

            return itemService.edit(itemId, itemRequest, file);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exception occurred while parsing json: " + ex.getMessage());
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ex.getMessage(), ex);
        }
    }
}