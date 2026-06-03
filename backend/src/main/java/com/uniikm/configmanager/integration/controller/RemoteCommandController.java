package com.uniikm.configmanager.integration.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uniikm.configmanager.integration.dto.CommandExecutionRequest;
import com.uniikm.configmanager.integration.dto.CommandGroupStatus;
import com.uniikm.configmanager.integration.dto.CommandTaskResult;
import com.uniikm.configmanager.integration.server.RemoteCommandService;

@RestController
@RequestMapping("/api/commands")
@RequiredArgsConstructor
public class RemoteCommandController {

    private final RemoteCommandService remoteCommandService;

    @PostMapping("/execute")
    public ResponseEntity<CommandGroupStatus> execute(@RequestBody CommandExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(remoteCommandService.executeAsync(request));
    }

    @GetMapping("/status/{taskGroupId}")
    public CommandGroupStatus getGroupStatus(@PathVariable String taskGroupId) {
        return remoteCommandService.getGroupStatus(taskGroupId);
    }

    @GetMapping("/tasks/{taskId}")
    public CommandTaskResult getTaskStatus(@PathVariable String taskId) {
        return remoteCommandService.getTaskResult(taskId);
    }
}
