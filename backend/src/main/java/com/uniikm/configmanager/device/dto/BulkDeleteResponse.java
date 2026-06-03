package com.uniikm.configmanager.device.dto;

import java.util.List;

public record BulkDeleteResponse(
    int successCount,
    int failCount,
    List<DeleteError> errors
) {}