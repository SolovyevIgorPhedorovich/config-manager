package com.uniikm.configmanager.config.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record ConfigHistoryEntry(
    Long versionId,
    Integer versionNum,
    LocalDateTime createdAt,
    String checksum,
    Long parentVersionId,
    JsonNode configData        // полный конфиг этой версии
){}
