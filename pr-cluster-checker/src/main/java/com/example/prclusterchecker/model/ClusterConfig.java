package com.example.prclusterchecker.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterConfig {
    private String name;
    private String releaseBranch;
}
