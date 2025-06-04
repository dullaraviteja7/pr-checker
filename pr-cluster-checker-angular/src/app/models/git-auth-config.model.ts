export interface GitAuthConfig {
    method: 'pat' | 'userpass' | null;
    username?: string | null;
}
