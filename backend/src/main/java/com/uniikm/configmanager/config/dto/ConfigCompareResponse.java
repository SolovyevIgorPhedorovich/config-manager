package com.uniikm.configmanager.config.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ConfigCompareResponse(
    Long versionIdA,
    Long versionIdB,
    JsonNode diff,
    String checksumA,
    String checksumB
) {}