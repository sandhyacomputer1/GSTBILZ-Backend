package com.io;



import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {
	private Long id;
	private String categoryId;
	private String name;
	private String description;
	private String bgColor;
	private LocalDateTime createdAt;
	private LocalDateTime updateAt;
	private String imgUrl;
	private Integer items;
	
}
