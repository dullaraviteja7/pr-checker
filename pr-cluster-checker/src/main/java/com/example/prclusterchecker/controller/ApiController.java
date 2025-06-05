package com.example.prclusterchecker.controller;

import com.example.prclusterchecker.dto.AnalysisResultDto;
import com.example.prclusterchecker.dto.MissingPrDataDto;
import com.example.prclusterchecker.dto.PrCheckDetailDto;
import com.example.prclusterchecker.model.AppConfig;
import com.example.prclusterchecker.service.ConfigService;
import com.example.prclusterchecker.service.GitHubService;
import com.example.prclusterchecker.service.PrAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ConfigService configService;
    private final PrAnalysisService prAnalysisService;
    private final GitHubService gitHubService;

    @Autowired
    public ApiController(ConfigService configService, PrAnalysisService prAnalysisService, GitHubService gitHubService) {
        this.configService = configService;
        this.prAnalysisService = prAnalysisService;
        this.gitHubService = gitHubService;
    }

    // Configuration Endpoints
    @GetMapping("/config")
    public AppConfig getAppConfig() {
        return configService.loadAppConfig();
    }

    @PostMapping("/config")
    public ResponseEntity<Void> saveAppConfig(@RequestBody AppConfig appConfig) {
        configService.saveAppConfig(appConfig);
        return ResponseEntity.ok().build();
    }

    // Dashboard Endpoint
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData(
            @RequestParam Optional<String> dateFrom,
            @RequestParam Optional<String> dateTo,
            @RequestParam Optional<String> filterClusterName) {

        AppConfig currentAppConfig = configService.loadAppConfig();
        // Create a temporary config for this request, possibly overriding date ranges
        AppConfig requestSpecificConfig = new AppConfig();
        requestSpecificConfig.setGitRepoUrl(currentAppConfig.getGitRepoUrl());
        requestSpecificConfig.setAuthConfig(currentAppConfig.getAuthConfig());
        requestSpecificConfig.setMainReleaseBranch(currentAppConfig.getMainReleaseBranch());
        requestSpecificConfig.setClusters(currentAppConfig.getClusters());

        dateFrom.ifPresent(requestSpecificConfig::setDateRangeFrom);
        dateTo.ifPresent(requestSpecificConfig::setDateRangeTo);

        try {
            AnalysisResultDto analysisResult = prAnalysisService.analyzePrs(requestSpecificConfig);

            if (filterClusterName.isPresent() && !filterClusterName.get().isEmpty()) {
                String clusterToFilter = filterClusterName.get();
                Map<String, List<MissingPrDataDto>> filteredMap = analysisResult.getMissingPrsByCluster().entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(clusterToFilter))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                if (filteredMap.isEmpty() && !analysisResult.getMissingPrsByCluster().keySet().stream().anyMatch(k -> k.equalsIgnoreCase(clusterToFilter))) {
                     // If the filterClusterName doesn't exist in the original map keys, return an empty map for that specific cluster.
                    Map<String, List<MissingPrDataDto>> resultForNonExistentCluster = new HashMap<>();
                    resultForNonExistentCluster.put(clusterToFilter, List.of());
                    analysisResult.setMissingPrsByCluster(resultForNonExistentCluster);
                } else {
                    analysisResult.setMissingPrsByCluster(filteredMap);
                }
            }
            return ResponseEntity.ok(analysisResult);
        } catch (Exception e) { // Catch broader exceptions from analysis or GitHub interaction
            System.err.println("Error generating dashboard data: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error generating dashboard data: " + e.getMessage());
        }
    }

    // PR Checker Endpoint
    @GetMapping("/check_pr")
    public ResponseEntity<PrCheckDetailDto> checkPr(@RequestParam int prNumber) {
        AppConfig appConfig = configService.loadAppConfig();
        if (appConfig.getGitRepoUrl() == null || appConfig.getGitRepoUrl().isEmpty()) {
             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // Or a DTO with error message
        }
        try {
            Optional<PrCheckDetailDto> prDetails = prAnalysisService.getPrDetails(appConfig, prNumber);
            return prDetails
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("Error checking PR #" + prNumber + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Clear Cache Endpoint
    @PostMapping("/clear_cache")
    public ResponseEntity<Void> clearCacheApi() {
        gitHubService.clearCache();
        return ResponseEntity.ok().build();
    }
}
