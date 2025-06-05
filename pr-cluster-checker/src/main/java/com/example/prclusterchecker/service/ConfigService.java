package com.example.prclusterchecker.service;

import com.example.prclusterchecker.model.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import com.example.prclusterchecker.util.CustomPair; // For getGithubUserpass

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ConfigService {

    private static final String CONFIG_FILE_PATH = "data/config.json";
    private final ObjectMapper objectMapper;

    public ConfigService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // For pretty printing JSON
    }

    public AppConfig loadAppConfig() {
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists() && configFile.length() > 0) { // Check if file exists and is not empty
                return objectMapper.readValue(configFile, AppConfig.class);
            } else {
                // Create default config and save it if file doesn't exist or is empty
                AppConfig defaultConfig = new AppConfig();
                saveAppConfig(defaultConfig); // Save it so user has a template
                return defaultConfig;
            }
        } catch (IOException e) {
            System.err.println("Error loading config file: " + e.getMessage());
            // Return a default config in case of error
            return new AppConfig();
        }
    }

    public void saveAppConfig(AppConfig config) {
        try {
            Path configPath = Paths.get(CONFIG_FILE_PATH);
            // Create parent directory 'data' if it doesn't exist
            if (configPath.getParent() != null && !Files.exists(configPath.getParent())) {
                Files.createDirectories(configPath.getParent());
            }
            objectMapper.writeValue(new File(CONFIG_FILE_PATH), config);
        } catch (IOException e) {
            System.err.println("Error saving config file: " + e.getMessage());
            // Handle exception, perhaps log it more formally
        }
    }

    public String getGithubToken() {
        return System.getenv("GITHUB_TOKEN");
    }

    public CustomPair<String, String> getGithubUserpass() {
        String username = System.getenv("GITHUB_USERNAME");
        String password = System.getenv("GITHUB_PASSWORD");
        if (username != null && password != null) {
            return CustomPair.of(username, password);
        }
        return null; // Or throw an exception if credentials are not found but expected
    }
}
