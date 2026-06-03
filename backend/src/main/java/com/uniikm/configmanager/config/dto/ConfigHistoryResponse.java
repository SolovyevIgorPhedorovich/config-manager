package com.uniikm.configmanager.config.dto;

import java.util.List;

public record ConfigHistoryResponse(
    Long deviceId,
    List<ConfigHistoryEntry> history
) {}