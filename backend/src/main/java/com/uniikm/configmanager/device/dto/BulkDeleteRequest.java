package com.uniikm.configmanager.device.dto;

import java.util.List;

public record BulkDeleteRequest(
    List<Long> ids,
    String actor
) {}