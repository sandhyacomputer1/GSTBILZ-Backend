package com.service.impl;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.service.FileUploadService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private final Cloudinary cloudinary;

    @Override
    public String UploadFile(MultipartFile file) {

        try {

            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.emptyMap());
            
            System.out.println(uploadResult);

            return uploadResult.get("secure_url").toString();
            
        } catch (IOException e) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "File upload failed");
        }
    }
@Override
public void deleteFile(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
        return; // Nothing to delete
    }

    try {
        int lastSlash = imageUrl.lastIndexOf("/");
        int lastDot = imageUrl.lastIndexOf(".");
        
        if (lastSlash != -1 && lastDot != -1 && lastSlash < lastDot) {
            String publicId = imageUrl.substring(lastSlash + 1, lastDot);
            
            // Skip deleting the shared placeholder image
            if (!publicId.equals("placeholder")) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            }
        }
    } catch (Exception e) {
        // Log the error but DO NOT throw an exception, otherwise the database item won't be deleted
        System.err.println("Warning: Failed to delete image from Cloudinary: " + e.getMessage());
    }
}
}
