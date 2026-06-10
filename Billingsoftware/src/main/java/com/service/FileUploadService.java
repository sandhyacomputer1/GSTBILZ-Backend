package com.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {
	String UploadFile(MultipartFile file);
	void deleteFile(String imgUrl);
	

	
}
