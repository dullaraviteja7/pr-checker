package com.example.prclusterchecker.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrCheckDetailDto {
    private int prNumber;
    private String prTitle;
    private String prHtmlUrl;
    private boolean isMergedToMain;
    private String mergeCommitShaMain; // Optional

    @Builder.Default
    private List<String> markedClusters = new ArrayList<>();

    @Builder.Default
    private Map<String, Boolean> cherryPickStatusByCluster = new HashMap<>();
}
