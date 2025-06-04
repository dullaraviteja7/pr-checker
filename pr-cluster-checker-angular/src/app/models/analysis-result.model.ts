import { MissingPrData } from './missing-pr-data.model';

export interface AnalysisResult {
    missingPrsByCluster: { [clusterName: string]: MissingPrData[] };
}
