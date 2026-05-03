package com.project.configmanager.controller;

import com.project.configmanager.model.AuditLog;
import com.project.configmanager.repository.AuditLogRepository;
import com.project.configmanager.service.AnsibleExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deploy")
@RequiredArgsConstructor
public class DeployController {

    private final AnsibleExecutor ansibleExecutor;
    private final AuditLogRepository auditLogRepo;

    @PostMapping("/windows")
    public ResponseEntity<String> deployWindows(
            @RequestParam String ip,
            @RequestParam String hostname) {

        // Сохраняем лог
        AuditLog log = new AuditLog();
        log.setUserId("system");
        log.setActionType(com.project.configmanager.model.enums.AuditAction.DEVICE_ADDED);
        log.setTargetIp(ip);
        log.setStatus(com.project.configmanager.model.enums.TaskStatus.RUNNING);
        auditLogRepo.save(log);

        // Выполняем
        String result = ansibleExecutor.deployWindowsConfig(ip, hostname);

        // Обновляем статус
        AuditLog updatedLog = new AuditLog();
        updatedLog.setId(log.getId());
        updatedLog.setUserId(log.getUserId());
        updatedLog.setActionType(log.getActionType());
        updatedLog.setTargetIp(log.getTargetIp());
        updatedLog.setStatus(result.contains("ERROR") || result.contains("Exit code: 1") ? com.project.configmanager.model.enums.TaskStatus.FAILED : com.project.configmanager.model.enums.TaskStatus.SUCCESS);
        updatedLog.setCreatedAt(java.time.LocalDateTime.now());
        auditLogRepo.save(updatedLog);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public Iterable<AuditLog> getHistory() {
        return auditLogRepo.findAll();
    }
}