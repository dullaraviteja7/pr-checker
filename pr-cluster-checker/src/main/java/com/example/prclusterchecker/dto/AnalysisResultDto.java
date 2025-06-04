package com.example.prclusterchecker.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResultDto {
    @Builder.Default
    private Map<String, List<MissingPrDataDto>> missingPrsByCluster = new HashMap<>();
}
