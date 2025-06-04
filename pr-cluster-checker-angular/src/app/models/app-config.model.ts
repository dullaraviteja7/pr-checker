import { GitAuthConfig } from './git-auth-config.model';
import { ClusterConfig } from './cluster-config.model';

export interface AppConfig {
    gitRepoUrl: string | null;
    authConfig: GitAuthConfig;
    mainReleaseBranch: string | null;
    clusters: ClusterConfig[];
    dateRangeFrom: string | null; // Should be in YYYY-MM-DD format
    dateRangeTo: string | null;   // Should be in YYYY-MM-DD format
}
