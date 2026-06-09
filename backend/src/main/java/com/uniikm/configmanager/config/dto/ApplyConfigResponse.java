package com.uniikm.configmanager.config.dto;

import java.util.List;

public record ApplyConfigResponse(
    String batchId,
    List<String> taskGroupIds,
    // Устройства, для которых применение отложено (были офлайн) и будет выполнено
    // автоматически при появлении в сети.
    List<Long> scheduledDeviceIds
) {}
