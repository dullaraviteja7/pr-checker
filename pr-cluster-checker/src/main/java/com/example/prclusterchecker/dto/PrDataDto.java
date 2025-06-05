package com.example.prclusterchecker.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Date; // For mergedAt, if we decide to use Date type
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrDataDto {
    private int number;
    private String title;
    private String body;
    private String htmlUrl;
    private Date mergedAt; // org.kohsuke.github.GHPullRequest.getMergedAt() returns Date
    private String mergeCommitSha;
    private List<String> targetClusters;
}
