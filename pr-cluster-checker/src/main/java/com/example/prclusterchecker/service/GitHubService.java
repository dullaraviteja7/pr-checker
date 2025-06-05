package com.example.prclusterchecker.service;

import com.example.prclusterchecker.dto.PrDataDto;
import com.example.prclusterchecker.model.AppConfig;
import com.example.prclusterchecker.model.GitAuthConfig;
import com.example.prclusterchecker.util.CustomPair;
import org.kohsuke.github.*;
import org.kohsuke.github.GHCompare.Status; // Explicit import for the enum
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap; // For cache
import java.util.Date; // For PrDataDto and GHPullRequest
import java.util.HashMap; // For getPrsInDateRange return
import java.util.Optional; // For getPrMergeCommitSha return

@Service
public class GitHubService {

    private final ConfigService configService;
    private GitHub gitHubClient; // Cache the client
    private final Map<String, Object> generalCache = new ConcurrentHashMap<>();

    @Autowired
    public GitHubService(ConfigService configService) {
        this.configService = configService;
    }

    private GitHub getGitHubClient() throws IOException {
        if (this.gitHubClient == null) {
            AppConfig appConfig = configService.loadAppConfig();
            GitAuthConfig authConfig = appConfig.getAuthConfig();
            GitHubBuilder githubBuilder = new GitHubBuilder();

            if (authConfig != null && authConfig.getMethod() != null) {
                switch (authConfig.getMethod().toLowerCase()) {
                    case "pat":
                        String token = configService.getGithubToken();
                        if (token == null || token.isEmpty()) {
                            throw new IOException("GitHub PAT token not found in environment variable GITHUB_TOKEN.");
                        }
                        githubBuilder.withOAuthToken(token);
                        break;
                    case "userpass":
                        CustomPair<String, String> userPass = configService.getGithubUserpass();
                        if (userPass == null || userPass.left() == null || userPass.right() == null) {
                            throw new IOException("GitHub username/password not found in environment variables.");
                        }
                        githubBuilder.withPassword(userPass.left(), userPass.right());
                        break;
                    default:
                        // Anonymous access or misconfiguration
                        System.out.println("Warning: GitHub client initialized with anonymous access or auth method not recognized.");
                        break;
                }
            } else {
                // Fallback to anonymous if no auth config
                 System.out.println("Warning: GitHub client initialized with anonymous access due to missing auth config.");
            }
            this.gitHubClient = githubBuilder.build();
        }
        return this.gitHubClient;
    }

    public List<String> parsePrBodyForClusters(String body) {
        List<String> clusters = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return clusters;
        }
        // Regex: ```cluster\s*\n(.*?)``` or ```cluster\s+(.*?)\s+``` (single line)
        // Looking for blocks like:
        // ```cluster
        // cluster-a
        // cluster-b
        // ```
        // OR single line: ```cluster cluster-a cluster-b ``` (less likely based on Python)
        // The Python regex `r"```cluster\s*\n([^`]*?)```"` captures multi-line content inside ```cluster ... ```
        // We need to split the captured content by lines or spaces.

        // Pattern for the block: ```cluster ... ```
        Pattern blockPattern = Pattern.compile("```cluster\\s*\\n([^`]*)```", Pattern.MULTILINE);
        Matcher blockMatcher = blockPattern.matcher(body);

        if (blockMatcher.find()) {
            String contentInsideBlock = blockMatcher.group(1).trim();
            // Split by newline and then trim each line
            String[] lines = contentInsideBlock.split("\\r?\\n");
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty()) {
                    clusters.add(trimmedLine);
                }
            }
        }
        // If no block found, try the single-line pattern from Python: `cluster:\s*(.*)`
        // Python: `re.search(r"cluster:\s*(.*)", pr_body, re.IGNORECASE)`
        // and then `value.split()`
        if (clusters.isEmpty()) {
            // Enable DOTALL mode so that '.' matches newline characters
            Pattern singleLinePattern = Pattern.compile("cluster:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher singleLineMatcher = singleLinePattern.matcher(body);
            if (singleLineMatcher.find()) {
                String content = singleLineMatcher.group(1).trim();
                String[] parts = content.split("\\s+"); // Split by one or more spaces
                for (String part : parts) {
                    String trimmedPart = part.trim();
                    if (!trimmedPart.isEmpty()) {
                        clusters.add(trimmedPart);
                    }
                }
            }
        }
        return clusters;
    }

    // Placeholder for other methods to be implemented
    @SuppressWarnings("unchecked")
    public Map<Integer, PrDataDto> getPrsInDateRange(String repoUrlString, String mainBranch, Date fromDate, Date toDate) throws IOException {
        String fromDateStr = (fromDate == null) ? "null" : String.valueOf(fromDate.getTime());
        String toDateStr = (toDate == null) ? "null" : String.valueOf(toDate.getTime());
        String cacheKey = "prs:" + repoUrlString + ":" + mainBranch + ":" + fromDateStr + ":" + toDateStr;

        if (generalCache.containsKey(cacheKey)) {
            try {
                return (Map<Integer, PrDataDto>) generalCache.get(cacheKey);
            } catch (ClassCastException e) {
                System.err.println("Cache type mismatch for key: " + cacheKey + ", error: " + e.getMessage());
                generalCache.remove(cacheKey); // Remove corrupted entry
            }
        }

        GHRepository ghRepository = getRepository(repoUrlString);
        Map<Integer, PrDataDto> prMap = new HashMap<>();

        // Query for closed pull requests targeting the mainBranch
        PagedIterable<GHPullRequest> pullRequests = ghRepository.queryPullRequests()
                .state(GHIssueState.CLOSED)
                .base(mainBranch)
                .list();

        for (GHPullRequest pr : pullRequests) {
            if (pr.isMerged() && pr.getMergedAt() != null) {
                Date mergedAt = pr.getMergedAt();
                boolean inRange = true;
                if (fromDate != null && mergedAt.before(fromDate)) {
                    inRange = false;
                }
                if (toDate != null && mergedAt.after(toDate)) {
                    inRange = false;
                }

                if (inRange) {
                    List<String> targetClusters = parsePrBodyForClusters(pr.getBody());
                    PrDataDto prData = new PrDataDto(
                            pr.getNumber(),
                            pr.getTitle(),
                            pr.getBody(),
                            pr.getHtmlUrl().toString(),
                            pr.getMergedAt(),
                            pr.getMergeCommitSha(), // This can be null if not yet merged or retrieved
                            targetClusters
                    );
                    prMap.put(pr.getNumber(), prData);
                }
            }
        }
        generalCache.put(cacheKey, prMap);
        return prMap;
    }

    @SuppressWarnings("unchecked")
    public Optional<String> getPrMergeCommitSha(String repoUrlString, int prNumber) throws IOException {
        String cacheKey = "mergeSha:" + repoUrlString + ":" + prNumber;
        if (generalCache.containsKey(cacheKey)) {
            try {
                return (Optional<String>) generalCache.get(cacheKey);
            } catch (ClassCastException e) {
                System.err.println("Cache type mismatch for key: " + cacheKey + ", error: " + e.getMessage());
                generalCache.remove(cacheKey); // Remove corrupted entry
            }
        }

        GHRepository ghRepository = getRepository(repoUrlString);
        try {
            GHPullRequest pr = ghRepository.getPullRequest(prNumber);
            if (pr != null && pr.isMerged()) {
                String mergeSha = pr.getMergeCommitSha();
                Optional<String> result = Optional.ofNullable(mergeSha);
                generalCache.put(cacheKey, result);
                return result;
            }
        } catch (IOException e) {
            System.err.println("Error getting PR merge commit SHA for PR #" + prNumber + ": " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                Optional<String> emptyResult = Optional.empty();
                generalCache.put(cacheKey, emptyResult); // Cache negative result for not found
                return emptyResult;
            }
            throw e;
        }
        Optional<String> emptyResultOutsideTry = Optional.empty();
        generalCache.put(cacheKey, emptyResultOutsideTry);
        return emptyResultOutsideTry;
    }

    public boolean isCommitInBranch(String repoUrlString, String branchName, String commitSha) throws IOException {
        String cacheKey = "commitInBranch:" + repoUrlString + ":" + branchName + ":" + commitSha;
        if (generalCache.containsKey(cacheKey)) {
            try {
                return (Boolean) generalCache.get(cacheKey);
            } catch (ClassCastException e) {
                System.err.println("Cache type mismatch for key: " + cacheKey + ", error: " + e.getMessage());
                generalCache.remove(cacheKey); // Remove corrupted entry
            }
        }

        GHRepository ghRepository = getRepository(repoUrlString);
        try {
            // Ensure commitSha is a full SHA, though the library might handle short SHAs.
            // The 'base' is commitSha, the 'head' is branchName.
            // We are checking if branchName is ahead of or identical to commitSha.
            GHCompare comparison = ghRepository.getCompare(commitSha, branchName);

            // If branchName (head) is ahead of commitSha (base), or they are identical,
            // then commitSha is in the history of branchName.
            Status statusValue = comparison.getStatus(); // Use the imported Status
            if (statusValue == null) {
                // This case should ideally not happen if comparison is successful
                // and commit/branch exist. Consider logging an error or throwing.
                return false;
            }
            // Workaround for "cannot find symbol" on enum constants
            String statusName = statusValue.name();
            boolean result = "AHEAD".equals(statusName) || "IDENTICAL".equals(statusName);
            generalCache.put(cacheKey, result);
            return result;

        } catch (IOException e) {
            System.err.println("Error comparing commit " + commitSha + " with branch " + branchName + ": " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                generalCache.put(cacheKey, false); // Cache negative result for not found
                return false;
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<PrDataDto> getSinglePrDetails(String repoUrlString, int prNumber) throws IOException {
        String cacheKey = "prDetails:" + repoUrlString + ":" + prNumber;
        if (generalCache.containsKey(cacheKey)) {
            try {
                return (Optional<PrDataDto>) generalCache.get(cacheKey);
            } catch (ClassCastException e) {
                System.err.println("Cache type mismatch for key: " + cacheKey + ", error: " + e.getMessage());
                generalCache.remove(cacheKey); // Remove corrupted entry
            }
        }

        GHRepository ghRepository = getRepository(repoUrlString);
        try {
            GHPullRequest pr = ghRepository.getPullRequest(prNumber);
            if (pr != null) {
                List<String> targetClusters = parsePrBodyForClusters(pr.getBody());
                PrDataDto prData = new PrDataDto(
                        pr.getNumber(),
                        pr.getTitle(),
                        pr.getBody(),
                        pr.getHtmlUrl().toString(),
                        pr.isMerged() ? pr.getMergedAt() : null, // Only set mergedAt if PR is merged
                        pr.isMerged() ? pr.getMergeCommitSha() : null,
                        targetClusters
                );
                Optional<PrDataDto> result = Optional.of(prData);
                generalCache.put(cacheKey, result);
                return result;
            }
        } catch (IOException e) {
            System.err.println("Error getting details for PR #" + prNumber + ": " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                Optional<PrDataDto> emptyResult = Optional.empty();
                generalCache.put(cacheKey, emptyResult); // Cache negative result for not found
                return emptyResult;
            }
            throw e;
        }
        Optional<PrDataDto> emptyResultOutsideTry = Optional.empty();
        generalCache.put(cacheKey, emptyResultOutsideTry);
        return emptyResultOutsideTry;
    }

    public void clearCache() {
        generalCache.clear();
        System.out.println("GitHubService cache cleared.");
    }

    // Helper to get GHRepository
    private GHRepository getRepository(String repoUrlString) throws IOException {
        GitHub client = getGitHubClient();
        // Example repoUrlString: "https://github.com/owner/repo" or "owner/repo"
        String repoPath = repoUrlString.replace("https://github.com/", "");
        if (repoPath.endsWith(".git")) {
            repoPath = repoPath.substring(0, repoPath.length() - 4);
        }
        return client.getRepository(repoPath);
    }
}
