package com.uniikm.configmanager.config.event;

import java.util.Map;

public record ConfigApplyEvent(
    String eventType,
    String aggregateType,
    Long aggregateId,
    Map<String, Object> beforeStates,
    Map<String, Object> afterState
) {}
