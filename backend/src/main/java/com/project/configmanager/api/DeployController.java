package com.project.configmanager.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deploy")
@RequiredArgsConstructor
public class DeployController {

    /*
    private final AnsibleExecutor ansibleExecutor;
    private final AuditLogRepository auditLogRepo;

    @PostMapping("/windows")
    public ResponseEntity<String> deployWindows(
            @RequestParam String ip,
            @RequestParam String hostname) {

        // Сохраняем лог
        AuditEvent log = new AuditEvent();
        log.setUserId("system");
        log.setActionType(com.project.configmanager.audit.enums.AuditAction.DEVICE_ADDED);
        log.setTargetIp(ip);
        log.setStatus(com.project.configmanager.audit.enums.TaskStatus.RUNNING);
        auditLogRepo.save(log);

        // Выполняем
        String result = ansibleExecutor.deployWindowsConfig(ip, hostname);

        // Обновляем статус
        AuditEvent updatedLog = new AuditEvent();
        updatedLog.setId(log.getId());
        updatedLog.setUserId(log.getUserId());
        updatedLog.setActionType(log.getActionType());
        updatedLog.setTargetIp(log.getTargetIp());
        updatedLog.setStatus(result.contains("ERROR") || result.contains("Exit code: 1") ? com.project.configmanager.audit.enums.TaskStatus.FAILED : com.project.configmanager.audit.enums.TaskStatus.SUCCESS);
        updatedLog.setCreatedAt(java.time.LocalDateTime.now());
        auditLogRepo.save(updatedLog);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public Iterable<AuditEvent> getHistory() {
        return auditLogRepo.findAll();
    }  */
}