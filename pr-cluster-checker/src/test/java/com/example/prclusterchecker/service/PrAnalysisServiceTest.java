package com.example.prclusterchecker.service;

import com.example.prclusterchecker.dto.AnalysisResultDto;
import com.example.prclusterchecker.dto.MissingPrDataDto;
import com.example.prclusterchecker.dto.PrDataDto;
import com.example.prclusterchecker.model.AppConfig;
import com.example.prclusterchecker.model.ClusterConfig;
import com.example.prclusterchecker.model.GitAuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq; // Added for eq()
import static org.mockito.Mockito.when;

public class PrAnalysisServiceTest {

    @Mock
    private ConfigService configService; // Mocked, PrAnalysisService depends on it

    @Mock
    private GitHubService gitHubService; // Mocked

    @InjectMocks
    private PrAnalysisService prAnalysisService;

    private AppConfig appConfig;
    private static final SimpleDateFormat DTO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        appConfig = new AppConfig();
        appConfig.setGitRepoUrl("https://github.com/test/repo.git");
        appConfig.setMainReleaseBranch("main");
        appConfig.setAuthConfig(new GitAuthConfig("pat", null));

        ClusterConfig cluster1 = new ClusterConfig("prod-us", "release/prod-us");
        ClusterConfig cluster2 = new ClusterConfig("staging-eu", "release/staging-eu");
        appConfig.setClusters(List.of(cluster1, cluster2));
        appConfig.setDateRangeFrom(null); // No date filter for these tests by default
        appConfig.setDateRangeTo(null);
    }

    @Test
    void analyzePrs_noPrsFound_returnsEmptyResult() throws IOException {
        when(gitHubService.getPrsInDateRange(anyString(), anyString(), any(), any()))
            .thenReturn(new HashMap<>());

        AnalysisResultDto result = prAnalysisService.analyzePrs(appConfig);

        assertNotNull(result);
        assertNotNull(result.getMissingPrsByCluster());
        assertTrue(result.getMissingPrsByCluster().get("prod-us").isEmpty());
        assertTrue(result.getMissingPrsByCluster().get("staging-eu").isEmpty());
    }

    @Test
    void analyzePrs_onePrMissingFromOneCluster() throws IOException {
        // PR1 marked for prod-us, staging-eu. Merged to main.
        // Found in prod-us branch, but NOT in staging-eu branch.
        Date mergedDate = new Date();
        PrDataDto pr1 = new PrDataDto(1, "Feat: Cool new thing", "Body with ```cluster\nprod-us\nstaging-eu\n```",
                                    "http://github.com/pr/1", mergedDate, "sha123", List.of("prod-us", "staging-eu"));

        Map<Integer, PrDataDto> prsOnMain = new HashMap<>();
        prsOnMain.put(1, pr1);

        when(gitHubService.getPrsInDateRange(eq(appConfig.getGitRepoUrl()), eq(appConfig.getMainReleaseBranch()), any(), any()))
            .thenReturn(prsOnMain);

        // getPrMergeCommitSha will be called if SHA is null in PrDataDto, ensure it's populated or mock it.
        // PrDataDto constructor populates it, so direct call to getPrMergeCommitSha might not happen if prData.getMergeCommitSha() is already "sha123"
        // Let's assume it's populated. If not, we'd mock:
        // when(gitHubService.getPrMergeCommitSha(appConfig.getGitRepoUrl(), 1)).thenReturn(Optional.of("sha123"));

        when(gitHubService.isCommitInBranch(appConfig.getGitRepoUrl(), "release/prod-us", "sha123"))
            .thenReturn(true); // Found in prod-us
        when(gitHubService.isCommitInBranch(appConfig.getGitRepoUrl(), "release/staging-eu", "sha123"))
            .thenReturn(false); // Missing from staging-eu

        AnalysisResultDto result = prAnalysisService.analyzePrs(appConfig);

        assertNotNull(result);
        assertTrue(result.getMissingPrsByCluster().get("prod-us").isEmpty(), "PR1 should not be missing from prod-us");
        assertEquals(1, result.getMissingPrsByCluster().get("staging-eu").size(), "PR1 should be missing from staging-eu");

        MissingPrDataDto missingPr = result.getMissingPrsByCluster().get("staging-eu").get(0);
        assertEquals(1, missingPr.getNumber());
        assertEquals("sha123", missingPr.getMergeCommitSha());
        assertEquals(DTO_DATE_FORMAT.format(mergedDate), missingPr.getMergedAt());
    }

    @Test
    void analyzePrs_prMarkedForNonConfiguredCluster_isIgnored() throws IOException {
        Date mergedDate = new Date();
        PrDataDto pr1 = new PrDataDto(1, "Feat: For special cluster", "Body with ```cluster\nspecial-ops\n```",
                                    "http://github.com/pr/1", mergedDate, "sha-special", List.of("special-ops"));
        Map<Integer, PrDataDto> prsOnMain = new HashMap<>();
        prsOnMain.put(1, pr1);

        when(gitHubService.getPrsInDateRange(anyString(), anyString(), any(), any())).thenReturn(prsOnMain);
        // isCommitInBranch should not even be called for "special-ops" as it's not in appConfig.getClusters()

        AnalysisResultDto result = prAnalysisService.analyzePrs(appConfig);

        assertNotNull(result);
        assertTrue(result.getMissingPrsByCluster().get("prod-us").isEmpty());
        assertTrue(result.getMissingPrsByCluster().get("staging-eu").isEmpty());
    }

    @Test
    void analyzePrs_gitHubServiceThrowsIOException_returnsEmptyResult() throws IOException {
        when(gitHubService.getPrsInDateRange(anyString(), anyString(), any(), any()))
            .thenThrow(new IOException("GitHub API error"));

        AnalysisResultDto result = prAnalysisService.analyzePrs(appConfig);

        assertNotNull(result);
        assertTrue(result.getMissingPrsByCluster().isEmpty(),
            "Result should be empty map, not map with empty lists for configured clusters, on major error.");
    }
}
