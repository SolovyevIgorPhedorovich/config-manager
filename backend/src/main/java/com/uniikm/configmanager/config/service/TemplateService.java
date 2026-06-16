package com.uniikm.configmanager.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniikm.configmanager.auth.model.User;
import com.uniikm.configmanager.auth.repository.UserRepository;
import com.uniikm.configmanager.auth.utils.SecurityFacade;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.DeviceCredentials;
import com.uniikm.configmanager.config.dto.TemplateApplyRequest;
import com.uniikm.configmanager.config.dto.TemplateAssignmentResponse;
import com.uniikm.configmanager.config.dto.TemplateRequest;
import com.uniikm.configmanager.config.dto.TemplateResponse;
import com.uniikm.configmanager.config.model.ConfigTemplate;
import com.uniikm.configmanager.config.model.TemplateAssignment;
import com.uniikm.configmanager.config.repository.ConfigTemplateRepository;
import com.uniikm.configmanager.config.repository.TemplateAssignmentRepository;
import com.uniikm.configmanager.device.facade.DeviceFacade;
import com.uniikm.configmanager.device.model.DeviceInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TemplateService {

    private final ConfigTemplateRepository templateRepo;
    private final TemplateAssignmentRepository assignmentRepo;
    private final DeviceFacade deviceFacade;
    private final UserRepository userRepo;
    private final ConfigOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;
    private final SecurityFacade securityFacade;

    // ──────────────────────────────────────────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────────────────────────────────────────

    public TemplateResponse create(TemplateRequest req) {
        if (templateRepo.existsByName(req.getName())) {
            throw new IllegalArgumentException("Шаблон с именем \"" + req.getName() + "\" уже существует");
        }
        User creator = currentUser();
        ConfigTemplate template = ConfigTemplate.builder()
                .name(req.getName())
                .description(req.getDescription())
                .content(req.getContent())
                .isActive(true)
                .createdBy(creator)
                .build();
        ConfigTemplate saved = templateRepo.save(template);
        log.info("Template '{}' (id={}) created by {}", saved.getName(), saved.getId(),
                creator != null ? creator.getUsername() : "system");
        return toResponse(saved, 0);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> getAll() {
        return templateRepo.findAllByIsActiveTrue().stream()
                .map(t -> toResponse(t, assignmentRepo.countByTemplate_Id(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse getById(Long id) {
        ConfigTemplate template = findActive(id);
        return toResponse(template, assignmentRepo.countByTemplate_Id(id));
    }

    public TemplateResponse update(Long id, TemplateRequest req) {
        ConfigTemplate template = findActive(id);
        if (!template.getName().equals(req.getName()) &&
                templateRepo.existsByNameAndIdNot(req.getName(), id)) {
            throw new IllegalArgumentException("Шаблон с именем \"" + req.getName() + "\" уже существует");
        }
        template.setName(req.getName());
        template.setDescription(req.getDescription());
        template.setContent(req.getContent());
        ConfigTemplate saved = templateRepo.save(template);
        log.info("Template '{}' (id={}) updated", saved.getName(), saved.getId());
        return toResponse(saved, assignmentRepo.countByTemplate_Id(id));
    }

    public void deactivate(Long id) {
        ConfigTemplate template = findActive(id);
        template.setIsActive(false);
        templateRepo.save(template);
        log.info("Template '{}' (id={}) deactivated", template.getName(), id);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Assignments
    // ──────────────────────────────────────────────────────────────────────────

    public List<TemplateAssignmentResponse> assign(Long templateId, List<Long> deviceIds) {
        ConfigTemplate template = findActive(templateId);
        User assigner = currentUser();
        List<TemplateAssignmentResponse> created = new ArrayList<>();

        for (Long deviceId : deviceIds) {
            if (assignmentRepo.existsByTemplate_IdAndDevice_Id(templateId, deviceId)) {
                log.debug("Template {} already assigned to device {}, skipping", templateId, deviceId);
                continue;
            }
            DeviceInfo device = deviceFacade.findDeviceEntity(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено: " + deviceId));

            TemplateAssignment assignment = TemplateAssignment.builder()
                    .template(template)
                    .device(device)
                    .assignedBy(assigner)
                    .build();
            TemplateAssignment saved = assignmentRepo.save(assignment);
            created.add(toAssignmentResponse(saved));
            log.info("Template '{}' assigned to device '{}' by {}",
                    template.getName(), device.getHostname(),
                    assigner != null ? assigner.getUsername() : "system");
        }
        return created;
    }

    public void unassign(Long templateId, Long deviceId) {
        if (!assignmentRepo.existsByTemplate_IdAndDevice_Id(templateId, deviceId)) {
            throw new IllegalArgumentException(
                    "Назначение шаблона " + templateId + " устройству " + deviceId + " не найдено");
        }
        assignmentRepo.deleteByTemplate_IdAndDevice_Id(templateId, deviceId);
        log.info("Template {} unassigned from device {}", templateId, deviceId);
    }

    @Transactional(readOnly = true)
    public List<TemplateAssignmentResponse> getAssignments(Long templateId) {
        findActive(templateId);
        return assignmentRepo.findByTemplateId(templateId).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateAssignmentResponse> getDeviceAssignments(Long deviceId) {
        deviceFacade.findDeviceEntity(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено: " + deviceId));
        return assignmentRepo.findByDeviceId(deviceId).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Apply
    // ──────────────────────────────────────────────────────────────────────────

    public ApplyConfigResponse apply(Long templateId, TemplateApplyRequest req) {
        ConfigTemplate template = findActive(templateId);

        List<DeviceInfo> devices;
        if (req.getDeviceIds() != null && !req.getDeviceIds().isEmpty()) {
            devices = deviceFacade.getDeviceEntities(req.getDeviceIds());
            if (devices.isEmpty()) {
                throw new IllegalArgumentException("Ни одно из указанных устройств не найдено");
            }
        } else {
            devices = assignmentRepo.findByTemplateId(templateId).stream()
                    .map(TemplateAssignment::getDevice)
                    .toList();
            if (devices.isEmpty()) {
                throw new IllegalStateException(
                        "Шаблон не привязан ни к одному устройству. Укажите deviceIds или привяжите шаблон.");
            }
        }

        Map<Long, DeviceCredentials> credentials = req.getCredentials() != null
                ? req.getCredentials()
                : Map.of();

        String batchId = UUID.randomUUID().toString();
        List<String> allGroupTaskIds = new ArrayList<>();
        List<Long> allScheduledDeviceIds = new ArrayList<>();

        for (DeviceInfo device : devices) {
            DeviceCredentials creds = credentials.get(device.getId());
            if (creds == null) {
                log.warn("Нет учётных данных для устройства {} ({}), пропускаем",
                        device.getId(), device.getHostname());
                continue;
            }
            JsonNode rendered = renderContent(template.getContent(), device, req.getVariables());
            ApplyConfigResponse r = orchestrationService.applyConfiguration(
                    List.of(device), rendered, Map.of(device.getId(), creds), "TEMPLATE");
            allGroupTaskIds.addAll(r.taskGroupIds());
            allScheduledDeviceIds.addAll(r.scheduledDeviceIds());
            log.info("Template '{}' applied to device '{}' (taskGroupIds={}, scheduled={})",
                    template.getName(), device.getHostname(), r.taskGroupIds(), r.scheduledDeviceIds());
        }

        log.info("Template apply batch {} complete: {} task groups, {} scheduled",
                batchId, allGroupTaskIds.size(), allScheduledDeviceIds.size());
        return new ApplyConfigResponse(batchId, allGroupTaskIds, allScheduledDeviceIds);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Подставляет переменные в JSON-содержимое шаблона.
     * Встроенные: {{device.hostname}}, {{device.ip}}, {{device.type}}.
     * Пользовательские: любые ключи из переданного словаря variables.
     */
    private JsonNode renderContent(JsonNode content, DeviceInfo device, Map<String, String> variables) {
        try {
            String json = objectMapper.writeValueAsString(content);

            if (variables != null) {
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    json = json.replace("{{" + entry.getKey() + "}}", entry.getValue());
                }
            }

            String primaryIp = device.getIps().isEmpty()
                    ? device.getHostname()
                    : device.getIps().get(0).getIp();

            json = json.replace("{{device.hostname}}", device.getHostname());
            json = json.replace("{{device.ip}}", primaryIp);
            json = json.replace("{{device.type}}", device.getType().name());

            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка рендеринга шаблона для устройства " + device.getId(), e);
        }
    }

    private ConfigTemplate findActive(Long id) {
        ConfigTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден: " + id));
        if (!Boolean.TRUE.equals(t.getIsActive())) {
            throw new IllegalArgumentException("Шаблон деактивирован: " + id);
        }
        return t;
    }

    private User currentUser() {
        Long userId = securityFacade.currentUserId();
        if (userId == null) return null;
        return userRepo.findById(userId).orElse(null);
    }

    private TemplateResponse toResponse(ConfigTemplate t, int deviceCount) {
        return new TemplateResponse(
                t.getId(),
                t.getName(),
                t.getDescription(),
                t.getContent(),
                t.getIsActive(),
                t.getCreatedBy() != null ? t.getCreatedBy().getUsername() : null,
                t.getCreatedAt(),
                deviceCount
        );
    }

    private TemplateAssignmentResponse toAssignmentResponse(TemplateAssignment a) {
        DeviceInfo device = a.getDevice();
        String ip = device.getIps().isEmpty() ? null : device.getIps().get(0).getIp();
        return new TemplateAssignmentResponse(
                a.getId(),
                a.getTemplate().getId(),
                a.getTemplate().getName(),
                device.getId(),
                device.getHostname(),
                ip,
                a.getAssignedBy() != null ? a.getAssignedBy().getUsername() : null,
                a.getAssignedAt()
        );
    }
}
