package com.example.prclusterchecker.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitAuthConfig {
    private String method; // "pat", "userpass"
    private String username; // optional, primarily for "userpass"
    // For PAT, the token itself will be read from GITHUB_TOKEN env var, not stored here.
    // For userpass, password will be read from GITHUB_PASSWORD env var.
}
