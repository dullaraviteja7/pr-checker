package com.example.prclusterchecker.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissingPrDataDto {
    private int number;
    private String title;
    private String htmlUrl;
    private String mergeCommitSha;
    private List<String> targetClusters; // Clusters this PR was marked for
    private String mergedAt; // Using String for simplicity in this DTO, can be formatted
}
