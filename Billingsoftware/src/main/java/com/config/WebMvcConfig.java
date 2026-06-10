package com.config;

import java.io.File;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Create the local invoices folder if it doesn't exist
        File directory = new File("invoices");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        // Expose the invoices directory on url path "/invoices/**"
        registry.addResourceHandler("/invoices/**")
                .addResourceLocations("file:invoices/");
    }
}
