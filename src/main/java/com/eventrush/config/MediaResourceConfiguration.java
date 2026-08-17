package com.eventrush.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MediaResourceConfiguration implements WebMvcConfigurer {

    private final Path uploadDirectory;

    public MediaResourceConfiguration(
            @Value("${eventrush.media.upload-dir:./data/uploads}") String uploadDirectory
    ) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Files.createDirectories(uploadDirectory);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("cannot create media upload directory", exception);
        }
        registry.addResourceHandler("/media/**")
                .addResourceLocations(uploadDirectory.toUri().toString());
    }
}
