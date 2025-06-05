package com.example.prclusterchecker.service;

import com.example.prclusterchecker.dto.*;
import com.example.prclusterchecker.model.AppConfig;
import com.example.prclusterchecker.model.ClusterConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PrAnalysisService {

    private final ConfigService configService;
    private final GitHubService gitHubService;

    // ISO 8601 Date format for PR merged_at fields from GitHub and for config date ranges
    // The GitHub API client (Kohsuke) returns java.util.Date directly for mergedAt.
    // This SimpleDateFormat is for parsing date strings from AppConfig and formatting for DTOs.
    private static final SimpleDateFormat ISO_8601_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat DTO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");


    @Autowired
    public PrAnalysisService(ConfigService configService, GitHubService gitHubService) {
        this.configService = configService;
        this.gitHubService = gitHubService;
    }

    public AnalysisResultDto analyzePrs(AppConfig appConfig) {
        if (appConfig == null || appConfig.getGitRepoUrl() == null || appConfig.getGitRepoUrl().isEmpty() ||
            appConfig.getMainReleaseBranch() == null || appConfig.getMainReleaseBranch().isEmpty()) {
            System.err.println("Analysis cannot proceed: Git repository URL or main release branch is not configured.");
            return AnalysisResultDto.builder().missingPrsByCluster(new HashMap<>()).build();
        }

        String repoUrl = appConfig.getGitRepoUrl();
        String mainBranch = appConfig.getMainReleaseBranch();

        Date dateFrom = null;
        Date dateTo = null;
        try {
            if (appConfig.getDateRangeFrom() != null && !appConfig.getDateRangeFrom().isEmpty()) {
                dateFrom = ISO_8601_FORMAT.parse(appConfig.getDateRangeFrom());
            }
            if (appConfig.getDateRangeTo() != null && !appConfig.getDateRangeTo().isEmpty()) {
                // To make the 'to' date inclusive, we might need to advance it by a day or handle it in the filtering logic.
                // For now, parsing as is. The filtering logic in GitHubService.getPrsInDateRange handles this.
                dateTo = ISO_8601_FORMAT.parse(appConfig.getDateRangeTo());
            }
        } catch (ParseException e) {
            System.err.println("Error parsing date range from config: " + e.getMessage());
            // Decide if to proceed with no date filter or return error. For now, proceed without date filter.
        }

        Map<Integer, PrDataDto> mergedPrsOnMain;
        try {
            mergedPrsOnMain = gitHubService.getPrsInDateRange(repoUrl, mainBranch, dateFrom, dateTo);
        } catch (IOException e) {
            System.err.println("Error fetching PRs from GitHub: " + e.getMessage());
            return AnalysisResultDto.builder().missingPrsByCluster(new HashMap<>()).build();
        }

        Map<String, List<MissingPrDataDto>> missingPrsByCluster = new HashMap<>();
        for (ClusterConfig cluster : appConfig.getClusters()) {
            missingPrsByCluster.put(cluster.getName(), new ArrayList<>());
        }

        for (PrDataDto prData : mergedPrsOnMain.values()) {
            if (prData.getMergeCommitSha() == null || prData.getMergeCommitSha().isEmpty()) {
                // Attempt to fetch if missing - though getPrsInDateRange should populate it
                try {
                    Optional<String> shaOpt = gitHubService.getPrMergeCommitSha(repoUrl, prData.getNumber());
                    if (shaOpt.isPresent()) {
                        prData.setMergeCommitSha(shaOpt.get());
                    } else {
                        System.err.println("Skipping PR #" + prData.getNumber() + " as its merge commit SHA could not be retrieved.");
                        continue;
                    }
                } catch (IOException e) {
                    System.err.println("Error fetching merge SHA for PR #" + prData.getNumber() + ": " + e.getMessage());
                    continue;
                }
            }

            List<String> targetClustersForPr = prData.getTargetClusters();
            if (targetClustersForPr == null || targetClustersForPr.isEmpty()) {
                // If PR body parsing yielded no clusters, it can't be missing from any specific one based on marking.
                // Depending on requirements, could log this or consider it for all clusters.
                // For now, skipping if not explicitly marked.
                continue;
            }

            for (String targetClusterName : targetClustersForPr) {
                ClusterConfig matchedClusterConfig = appConfig.getClusters().stream()
                    .filter(cc -> cc.getName().equalsIgnoreCase(targetClusterName))
                    .findFirst()
                    .orElse(null);

                if (matchedClusterConfig != null) {
                    try {
                        boolean isInBranch = gitHubService.isCommitInBranch(repoUrl, matchedClusterConfig.getReleaseBranch(), prData.getMergeCommitSha());
                        if (!isInBranch) {
                            MissingPrDataDto missingPr = MissingPrDataDto.builder()
                                .number(prData.getNumber())
                                .title(prData.getTitle())
                                .htmlUrl(prData.getHtmlUrl())
                                .mergeCommitSha(prData.getMergeCommitSha())
                                .targetClusters(prData.getTargetClusters()) // Show all clusters it was marked for
                                .mergedAt(prData.getMergedAt() != null ? DTO_DATE_FORMAT.format(prData.getMergedAt()) : "N/A")
                                .build();
                            missingPrsByCluster.get(matchedClusterConfig.getName()).add(missingPr);
                        }
                    } catch (IOException e) {
                        System.err.println("Error checking commit " + prData.getMergeCommitSha() + " in branch " + matchedClusterConfig.getReleaseBranch() + ": " + e.getMessage());
                        // Optionally add to a list of errors or treat as missing
                    }
                }
            }
        }
        return AnalysisResultDto.builder().missingPrsByCluster(missingPrsByCluster).build();
    }

    // Placeholder for getPrDetails
    public Optional<PrCheckDetailDto> getPrDetails(AppConfig appConfig, int prNumber) {
        if (appConfig == null || appConfig.getGitRepoUrl() == null || appConfig.getGitRepoUrl().isEmpty()) {
            System.err.println("Cannot get PR details: Git repository URL is not configured.");
            return Optional.empty();
        }
        String repoUrl = appConfig.getGitRepoUrl();
        String mainReleaseBranch = appConfig.getMainReleaseBranch(); // May or may not be used directly if PR is fetched by number

        try {
            Optional<PrDataDto> prDataOpt = gitHubService.getSinglePrDetails(repoUrl, prNumber);

            if (!prDataOpt.isPresent()) {
                System.err.println("PR #" + prNumber + " not found in repository " + repoUrl);
                // Return details indicating not found, rather than empty optional, to provide some context
                return Optional.of(PrCheckDetailDto.builder()
                                .prNumber(prNumber)
                                .prTitle("Not Found")
                                .prHtmlUrl("")
                                .isMergedToMain(false)
                                .build());
            }

            PrDataDto prData = prDataOpt.get();
            String mainMergeSha = prData.getMergeCommitSha(); // This will be null if not merged
            boolean isMergedToMainBranch = mainMergeSha != null && !mainMergeSha.isEmpty();

            // If getSinglePrDetails doesn't confirm it's merged to the *main branch*, we might need an explicit check.
            // However, getSinglePrDetails returns mergeCommitSha which implies it was merged.
            // The context of "isMergedToMain" implies checking against the main configured branch.
            // GHPullRequest.isMerged() is general, GHPullRequest.getBase().getRef() gives target branch.
            // For now, we assume if prData.getMergeCommitSha() is present, it's considered merged in a general sense.
            // The more specific check of whether it's merged *to the main branch* is implicitly handled if
            // getSinglePrDetails was smart enough, or would need explicit check against pr.getBase().getRef().
            // For simplicity, we'll rely on mergeCommitSha being present as indication of merge.

            List<String> markedClusters = gitHubService.parsePrBodyForClusters(prData.getBody());
            Map<String, Boolean> cherryPickStatus = new HashMap<>();

            if (isMergedToMainBranch) { // Only check cherry-pick status if it has a merge SHA
                for (ClusterConfig cluster : appConfig.getClusters()) {
                    boolean isCherryPicked = gitHubService.isCommitInBranch(repoUrl, cluster.getReleaseBranch(), mainMergeSha);
                    cherryPickStatus.put(cluster.getName(), isCherryPicked);
                }
            } else { // If not merged to main, it cannot be cherry-picked from main to clusters
                 for (ClusterConfig cluster : appConfig.getClusters()) {
                    cherryPickStatus.put(cluster.getName(), false);
                }
            }

            return Optional.of(PrCheckDetailDto.builder()
                    .prNumber(prNumber)
                    .prTitle(prData.getTitle())
                    .prHtmlUrl(prData.getHtmlUrl())
                    .isMergedToMain(isMergedToMainBranch)
                    .mergeCommitShaMain(mainMergeSha)
                    .markedClusters(markedClusters)
                    .cherryPickStatusByCluster(cherryPickStatus)
                    .build());

        } catch (IOException e) {
            System.err.println("Error getting PR details for #" + prNumber + ": " + e.getMessage());
            return Optional.empty(); // Or a more specific error DTO
        }
    }
}
