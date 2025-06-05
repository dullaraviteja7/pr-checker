package com.example.prclusterchecker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GitHubServiceTest {

    // GitHubService has dependencies, but for parsePrBodyForClusters, they are not needed.
    // If testing other methods, @Mock ConfigService would be needed.
    @InjectMocks
    private GitHubService gitHubService;

    @Mock
    private ConfigService configService; // Mocked, not used by parsePrBodyForClusters

    @BeforeEach
    void setUp() {
        // Initializes mocks and injects them if constructor injection is used in GitHubService
        // or if fields are annotated with @InjectMocks and @Mock.
        // Since GitHubService's constructor takes ConfigService, Mockito will inject the mock.
        MockitoAnnotations.openMocks(this);
        // If GitHubService was instantiated directly:
        // gitHubService = new GitHubService(configService);
    }

    static Stream<Arguments> prBodyProvider() {
        return Stream.of(
            Arguments.of("Some text before\n```cluster\ncluster-a\ncluster-b\n```\nSome text after", List.of("cluster-a", "cluster-b")),
            Arguments.of("```cluster\n  cluster-c  \n\n cluster-d\n```", List.of("cluster-c", "cluster-d")),
            Arguments.of("No cluster block here.", List.of()),
            Arguments.of("```cluster\n```", List.of()), // Empty block
            Arguments.of("```cluster\n  \n```", List.of()), // Empty lines in block
            Arguments.of("Malformed ```cluster\ncluster-e", List.of()), // Malformed, no closing ```
            Arguments.of("cluster: cluster-f cluster-g", List.of("cluster-f", "cluster-g")),
            Arguments.of("CLUSTER:    cluster-h   cluster-i  ", List.of("cluster-h", "cluster-i")),
            Arguments.of("cluster: \n cluster-x \n cluster-y", List.of("cluster-x", "cluster-y")), // Test with newlines after "cluster:"
            Arguments.of("cluster: cluster-z", List.of("cluster-z")),
            Arguments.of("No cluster keyword", List.of()),
            Arguments.of("cluster:", List.of()), // Empty cluster list after keyword
            Arguments.of("```cluster\nClusterA\nCLUSTER_B\n```\ncluster: ClusterC", List.of("ClusterA", "CLUSTER_B")) // Block takes precedence
        );
    }

    @ParameterizedTest
    @MethodSource("prBodyProvider")
    void testParsePrBodyForClusters(String body, List<String> expectedClusters) {
        List<String> actualClusters = gitHubService.parsePrBodyForClusters(body);
        assertEquals(expectedClusters.size(), actualClusters.size(), "Number of clusters should match");
        assertTrue(expectedClusters.containsAll(actualClusters) && actualClusters.containsAll(expectedClusters),
                   "Expected clusters " + expectedClusters + " but got " + actualClusters);
    }

    @Test
    void testParsePrBodyForClusters_nullBody() {
        List<String> actualClusters = gitHubService.parsePrBodyForClusters(null);
        assertTrue(actualClusters.isEmpty(), "Should return empty list for null body");
    }

    @Test
    void testParsePrBodyForClusters_emptyBody() {
        List<String> actualClusters = gitHubService.parsePrBodyForClusters("");
        assertTrue(actualClusters.isEmpty(), "Should return empty list for empty body");
    }
}
