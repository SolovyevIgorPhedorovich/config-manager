package com.uniikm.configmanager.config.controller;

import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.TemplateApplyRequest;
import com.uniikm.configmanager.config.dto.TemplateAssignRequest;
import com.uniikm.configmanager.config.dto.TemplateAssignmentResponse;
import com.uniikm.configmanager.config.dto.TemplateRequest;
import com.uniikm.configmanager.config.dto.TemplateResponse;
import com.uniikm.configmanager.config.service.TemplateService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(req));
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> getAll() {
        return ResponseEntity.ok(templateService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemplateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TemplateRequest req) {
        return ResponseEntity.ok(templateService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        templateService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Assignments ───────────────────────────────────────────────────────────

    /** Привязать шаблон к одному или нескольким устройствам. */
    @PostMapping("/{id}/devices")
    public ResponseEntity<List<TemplateAssignmentResponse>> assign(
            @PathVariable Long id,
            @Valid @RequestBody TemplateAssignRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.assign(id, req.getDeviceIds()));
    }

    /** Отвязать шаблон от устройства. */
    @DeleteMapping("/{id}/devices/{deviceId}")
    public ResponseEntity<Void> unassign(@PathVariable Long id, @PathVariable Long deviceId) {
        templateService.unassign(id, deviceId);
        return ResponseEntity.noContent().build();
    }

    /** Список устройств, к которым привязан шаблон. */
    @GetMapping("/{id}/devices")
    public ResponseEntity<List<TemplateAssignmentResponse>> getAssignments(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getAssignments(id));
    }

    // ── Apply ─────────────────────────────────────────────────────────────────

    /**
     * Применить шаблон к устройствам.
     * Если deviceIds не указаны — применяется ко всем привязанным устройствам.
     * Поддерживает переменные: {{device.hostname}}, {{device.ip}}, {{device.type}} и пользовательские.
     */
    @PostMapping("/{id}/apply")
    public ResponseEntity<ApplyConfigResponse> apply(
            @PathVariable Long id,
            @Valid @RequestBody TemplateApplyRequest req) {
        return ResponseEntity.ok(templateService.apply(id, req));
    }
}
