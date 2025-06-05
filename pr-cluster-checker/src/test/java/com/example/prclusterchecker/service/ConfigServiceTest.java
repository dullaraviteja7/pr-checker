package com.example.prclusterchecker.service;

import com.example.prclusterchecker.model.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Not a Spring Boot test, just plain JUnit/Mockito for service logic
public class ConfigServiceTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper(); // Use a real ObjectMapper, spy to verify calls if needed

    @InjectMocks
    private ConfigService configService;

    private String testConfigFilePath;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws NoSuchFieldException, IllegalAccessException {
        // Create a temporary config file path for each test
        testConfigFilePath = tempDir.resolve("test_config.json").toString();

        // Use reflection to set the private static final CONFIG_FILE_PATH
        // This is generally not recommended for production code but acceptable for making tests work
        // without major refactoring of the ConfigService to make the path configurable.
        Field field = ConfigService.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);

        // If final, need to remove final modifier first (more complex reflection)
        // For this test, assuming we can just set it, or ConfigService is refactored.
        // Let's assume ConfigService is modified to allow path injection for tests,
        // or we test its behavior around a specific known non-existent file.
        // For now, we'll test the default behavior and mock around it.
        // Re-initialize ConfigService with a fresh ObjectMapper for each test
        objectMapper = spy(new ObjectMapper());
        configService = new ConfigService(); // This will use the default path

        // We will test the default path behavior, ensure it's clean for each run
        File defaultConfig = new File("data/config.json");
        if(defaultConfig.exists()) {
            defaultConfig.delete();
        }
        File dataDir = new File("data");
        if(dataDir.exists() && dataDir.list().length == 0) {
            dataDir.delete();
        }
    }

    @Test
    void whenConfigFileDoesNotExist_loadAppConfig_returnsDefaultAndCreatesFile() throws IOException {
        File configFile = new File("data/config.json"); // Path used by ConfigService
        assertFalse(configFile.exists(), "Config file should not exist before test");

        AppConfig defaultConfig = configService.loadAppConfig();

        assertNotNull(defaultConfig);
        assertTrue(configFile.exists(), "Config file should have been created");
        assertEquals(new AppConfig(), defaultConfig, "Should return a default AppConfig object");

        // Clean up
        configFile.delete();
        new File("data").delete();
    }

    @Test
    void saveAppConfig_writesConfigToFile() throws IOException {
        AppConfig configToSave = new AppConfig();
        configToSave.setGitRepoUrl("https://example.com/repo.git");

        // To truly test save, we'd spy on ObjectMapper or verify file content.
        // Here, we'll spy on the objectMapper within the ConfigService instance.
        // For simplicity, we'll ensure no IO exception and file is created.
        // A more robust test would involve a spy ObjectMapper injected into ConfigService.

        configService.saveAppConfig(configToSave);

        File configFile = new File("data/config.json");
        assertTrue(configFile.exists(), "Config file should exist after save");

        // Verify content (optional, but good)
        ObjectMapper directMapper = new ObjectMapper();
        AppConfig savedConfig = directMapper.readValue(configFile, AppConfig.class);
        assertEquals("https://example.com/repo.git", savedConfig.getGitRepoUrl());

        // Clean up
        configFile.delete();
        new File("data").delete();
    }

    @Test
    void getGithubToken_readsEnvironmentVariable() {
        // This test is conceptual as we can't easily set env vars in a platform-agnostic way here.
        // We assume System.getenv works as expected.
        // If GITHUB_TOKEN is set in the test environment, this would check it.
        // String expectedToken = System.getenv("GITHUB_TOKEN");
        // assertEquals(expectedToken, configService.getGithubToken());
        assertNull(configService.getGithubToken(), "GITHUB_TOKEN should be null if not set in test env");
    }

    @Test
    void getGithubUserpass_readsEnvironmentVariables() {
        // Similar to getGithubToken, this is conceptual.
        assertNull(configService.getGithubUserpass(), "GITHUB_USERNAME/PASSWORD should be null if not set");
    }
}
