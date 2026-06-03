package com.uniikm.configmanager.config.dto;

import java.util.List;

public record ApplyConfigResponse(
    String batchId,
    List<String> taskGroupIds
) {}