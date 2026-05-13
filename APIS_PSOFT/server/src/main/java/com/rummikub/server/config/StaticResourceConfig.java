package com.rummikub.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el directorio public/ como resource location con prioridad alta.
 * Los patrones de assets con extensión de fichero se resuelven aquí ANTES
 * de que cualquier @Controller pueda interceptarlos.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${STATIC_PUBLIC_PATH:/app/public/}")
    private String publicPath;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = publicPath.endsWith("/") ? publicPath : publicPath + "/";
        String fileLocation = location.startsWith("file:") ? location : "file:" + location;

        registry.addResourceHandler("/assets/**")
                .addResourceLocations(fileLocation + "assets/",
                        "classpath:/static/assets/",
                        "classpath:/public/assets/")
                .resourceChain(true);

        registry.addResourceHandler("/*.js", "/*.css", "/*.svg",
                        "/*.ico", "/*.png", "/*.jpg", "/*.webp", "/*.woff2", "/*.woff")
                .addResourceLocations(fileLocation,
                        "classpath:/static/",
                        "classpath:/public/")
                .resourceChain(true);

        registry.addResourceHandler("/index.html")
                .addResourceLocations(fileLocation,
                        "classpath:/static/",
                        "classpath:/public/")
                .resourceChain(true);
    }
}
