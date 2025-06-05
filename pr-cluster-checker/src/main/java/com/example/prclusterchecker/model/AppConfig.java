package com.example.prclusterchecker.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppConfig {
    private String gitRepoUrl;
    private GitAuthConfig authConfig = new GitAuthConfig(); // Initialize with default
    private String mainReleaseBranch;
    private List<ClusterConfig> clusters = new ArrayList<>(); // Initialize with empty list
    private String dateRangeFrom; // Optional, ISO 8601 date string e.g., "2023-01-01"
    private String dateRangeTo;   // Optional, ISO 8601 date string e.g., "2023-01-31"
}
