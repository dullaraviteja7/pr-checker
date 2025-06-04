export interface MissingPrData {
    number: number;
    title: string;
    htmlUrl: string;
    mergeCommitSha: string;
    targetClusters: string[]; // Clusters this PR was marked for
    mergedAt: string; // Or Date, keep as string for simplicity from backend DTO
}
