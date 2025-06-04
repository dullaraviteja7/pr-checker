export interface PrCheckDetail {
    prNumber: number;
    prTitle: string;
    prHtmlUrl: string;
    isMergedToMain: boolean;
    mergeCommitShaMain: string | null;
    markedClusters: string[];
    cherryPickStatusByCluster: { [clusterName: string]: boolean };
}
