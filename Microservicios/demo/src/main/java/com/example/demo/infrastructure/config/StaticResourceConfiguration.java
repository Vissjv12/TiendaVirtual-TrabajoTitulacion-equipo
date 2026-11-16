package com.example.demo.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfiguration implements WebMvcConfigurer {

    /**
     * Resuelve y asegura el directorio físico absoluto donde se almacenan los códigos QR de Deuna.
     */
    public static Path resolveQrUploadDirectory() {
        Path uploadPath = Paths.get("uploads", "qr-deuna").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo inicializar el directorio de cargas QR: " + uploadPath, ex);
        }
        return uploadPath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Servir archivos desde la ruta absoluta normalizada uploads/qr-deuna
        Path uploadPath = resolveQrUploadDirectory();
        String uploadLocation = uploadPath.toUri().toString();
        if (!uploadLocation.endsWith("/")) {
            uploadLocation += "/";
        }
        registry.addResourceHandler("/uploads/qr-deuna/**")
                .addResourceLocations(uploadLocation);
    }
}
