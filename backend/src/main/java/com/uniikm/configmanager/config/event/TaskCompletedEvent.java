package com.uniikm.configmanager.config.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TaskCompletedEvent {
    private final String groupTaskId;
    private final String status; 
    private final String output;
    private final String error;
}