package com.uniikm.configmanager.config.dto;

import java.time.LocalDateTime;

public record ConfigHistoryEntry(
    Long versionId,
    Integer versionNum,
    LocalDateTime createdAt,
    String checksum,
    Long parentVersionId
){}
