package com.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.io.ItemRequest;
import com.io.ItemResponse;

public interface ItemService {

    ItemResponse add(ItemRequest request, MultipartFile file);

    List<ItemResponse> fetchItems();

    void deleteItem(String itemId);

    ItemResponse edit(String itemId, ItemRequest request, MultipartFile file);
}