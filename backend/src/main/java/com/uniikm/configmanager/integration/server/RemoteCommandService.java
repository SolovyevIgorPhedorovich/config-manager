package com.uniikm.configmanager.integration.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniikm.configmanager.audit.enums.TaskStatus;
import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.config.event.TaskCompletedEvent;
import com.uniikm.configmanager.integration.adater.ProtocolAdapter;
import com.uniikm.configmanager.integration.dto.CommandExecutionRequest;
import com.uniikm.configmanager.integration.dto.CommandGroupStatus;
import com.uniikm.configmanager.integration.dto.CommandTaskResult;
import com.uniikm.configmanager.integration.factory.ProtocolAdapterFactory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RemoteCommandService {

    private static final int DEFAULT_SSH_PORT = 22;
    private static final int DEFAULT_WINRM_PORT = 5985;
    private static final int DEFAULT_SNMP_PORT = 161;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;
    private final ProtocolAdapterFactory adapterFactory;
    private final ApplicationEventPublisher eventPublisher;

    public RemoteCommandService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Qualifier("taskExecutor") Executor taskExecutor,
            ProtocolAdapterFactory adapterFactory,
            ApplicationEventPublisher eventPublisher
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.adapterFactory = adapterFactory;
        this.eventPublisher = eventPublisher;
    }

    @Value("${remote-command.redis-key-prefix:remote-command}")
    private String redisKeyPrefix;

    @Value("${remote-command.result-ttl:PT24H}")
    private Duration resultTtl;

    @Value("${remote-command.default-timeout:PT60S}")
    private Duration defaultTimeout;

    @Value("${remote-command.max-targets:50}")
    private int maxTargets;


    public CommandGroupStatus executeAsync(CommandExecutionRequest request) {
        validateRequest(request);

        String groupTaskId = UUID.randomUUID().toString();
        List<CommandTaskResult> queuedTasks = new ArrayList<>();

        for (DeviceCommandTarget target : request.targets()) {
            String taskId = UUID.randomUUID().toString();
            CommandTaskResult queued = new CommandTaskResult(
                    taskId,
                    groupTaskId,
                    target.host().trim(),
                    target.resolvedProtocol(),
                    TaskStatus.QUEUED,
                    null,
                    null,
                    null,
                    LocalDateTime.now(),
                    null,
                    null
            );
            queuedTasks.add(queued);
            saveTaskResult(queued);
            redisTemplate.opsForList().rightPush(groupTasksKey(groupTaskId), taskId);
        }

        redisTemplate.expire(groupTasksKey(groupTaskId), resultTtl);

        for (int i = 0; i < request.targets().size(); i++) {
            DeviceCommandTarget target = request.targets().get(i);
            CommandTaskResult queued = queuedTasks.get(i);
            Duration timeout = resolveTimeout(request);
            CompletableFuture.runAsync(() -> runDeviceTask(request.command(), target, queued, timeout), taskExecutor);
        }

        return buildGroupStatus(groupTaskId, queuedTasks);
    }

    public CommandGroupStatus getGroupStatus(String groupTaskId) {
        List<String> taskIds = redisTemplate.opsForList().range(groupTasksKey(groupTaskId), 0, -1);
        if (taskIds == null || taskIds.isEmpty()) {
            throw new IllegalArgumentException("Задача не найдена: " + groupTaskId);
        }

        List<CommandTaskResult> results = taskIds.stream()
                .map(this::getTaskResult)
                .toList();

        return buildGroupStatus(groupTaskId, results);
    }

    public CommandTaskResult getTaskResult(String taskId) {
        String value = redisTemplate.opsForValue().get(taskKey(taskId));
        if (value == null) {
            throw new IllegalArgumentException("Задача не найдена: " + taskId);
        }
        try {
            return objectMapper.readValue(value, CommandTaskResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось прочитать результат задачи " + taskId, e);
        }
    }

    private void runDeviceTask(String command, DeviceCommandTarget target, CommandTaskResult queued, Duration timeout) {
        CommandTaskResult running = new CommandTaskResult(
                queued.taskId(),
                queued.groupTaskId(),
                queued.host(),
                queued.protocol(),
                TaskStatus.RUNNING,
                null,
                null,
                null,
                queued.queuedAt(),
                LocalDateTime.now(),
                null
        );
        saveTaskResult(running);

        ProtocolAdapter adapter = null;
        try {
            DeviceCommandTarget adapterRequest = new DeviceCommandTarget(
                    target.host(),
                    resolvePort(target),
                    target.username(),
                    target.password(),
                    target.resolvedProtocol(),
                    target.community(), null, null
            );

            adapter = adapterFactory.create(adapterRequest);
            
            if (adapter == null) {
                throw new IllegalStateException("Adapter is null for protocol: " + target.resolvedProtocol());
            }
            
            Map<String, Object> connectResult = adapter.connect(adapterRequest).get(timeout.toSeconds(), TimeUnit.SECONDS);
            
            if (!Boolean.TRUE.equals(connectResult.get("success"))) {
                String errorMsg = "Connection failed: " + connectResult.getOrDefault("error", "unknown error");
                saveFailure(queued, running.startedAt(), errorMsg);
                return;
            }

            Map<String, Object> execResult = adapter.executeCommand(command).get(timeout.toSeconds(), TimeUnit.SECONDS);
            boolean success = Boolean.TRUE.equals(execResult.get("success"));
            Integer exitCode = (Integer) execResult.getOrDefault("exitCode", -1);
            String stdout = (String) execResult.getOrDefault("stdout", "");
            String stderr = (String) execResult.getOrDefault("stderr", "");

            TaskStatus finalStatus = success ? TaskStatus.SUCCESS : TaskStatus.FAILED;
            CommandTaskResult finalResult = new CommandTaskResult(
                    queued.taskId(),
                    queued.groupTaskId(),
                    queued.host(),
                    queued.protocol(),
                    finalStatus,
                    exitCode,
                    stdout,
                    success ? null : stderr,
                    queued.queuedAt(),
                    running.startedAt(),
                    LocalDateTime.now()
            );
            saveTaskResult(finalResult);

            publishTaskCompletedEvent(queued.groupTaskId(), finalStatus, stdout, stderr);

        } catch (Exception e) {
            log.warn("Remote command failed for {} via {}", target.host(), target.resolvedProtocol(), e);
            saveFailure(queued, running.startedAt(), e.getMessage());
            publishTaskCompletedEvent(queued.groupTaskId(), TaskStatus.FAILED, null, e.getMessage());
        } finally {
            if (adapter != null && adapter.isConnected()) {
                try {
                    adapter.disconnect().get(5, TimeUnit.SECONDS);
                } catch (Exception ignore) {
                    log.debug("Disconnect error for {}: {}", target.host(), ignore.getMessage());
                }
            }
        }
    }

    private void publishTaskCompletedEvent(String groupTaskId, TaskStatus status, String output, String error) {
        TaskCompletedEvent event = new TaskCompletedEvent(
            groupTaskId,
            status.name(),
            output,
            error
        );
        eventPublisher.publishEvent(event);
        log.debug("Published TaskCompletedEvent for groupTaskId: {}, status: {}", groupTaskId, status);
    }

    private void saveFailure(CommandTaskResult queued, LocalDateTime startedAt, String errorMessage) {
        CommandTaskResult failed = new CommandTaskResult(
                queued.taskId(),
                queued.groupTaskId(),
                queued.host(),
                queued.protocol(),
                TaskStatus.FAILED,
                -1,
                null,
                errorMessage,
                queued.queuedAt(),
                startedAt,
                LocalDateTime.now()
        );
        saveTaskResult(failed);
    }

    private Map<String, String> buildAdapterConfig(DeviceCommandTarget target, Duration timeout) {
        Map<String, String> config = new HashMap<>();
        config.put("host", target.host());
        config.put("port", String.valueOf(resolvePort(target)));
        config.put("timeout", String.valueOf(timeout.toMillis()));

        if (target.resolvedProtocol() == ConnectionProtocol.SSH) {
            if (StringUtils.hasText(target.username())) config.put("username", target.username());
            if (StringUtils.hasText(target.password())) config.put("password", target.password());
        } else if (target.resolvedProtocol() == ConnectionProtocol.WINRM) {
            if (StringUtils.hasText(target.username())) config.put("username", target.username());
            if (StringUtils.hasText(target.password())) config.put("password", target.password());
            config.put("useHttps", String.valueOf(target.resolvedUseSsl()));
            config.put("disableCertificateValidation", String.valueOf(target.resolvedSkipCertificateCheck()));
        } else if (target.resolvedProtocol() == ConnectionProtocol.SNMP) {
            config.put("community", target.community() != null ? target.community() : "public");
        }
        return config;
    }

    private int resolvePort(DeviceCommandTarget target) {
        if (target.port() != null) return target.port();
        return switch (target.resolvedProtocol()) {
            case SSH -> DEFAULT_SSH_PORT;
            case WINRM -> DEFAULT_WINRM_PORT;
            case SNMP -> DEFAULT_SNMP_PORT;
        };
    }

    private Duration resolveTimeout(CommandExecutionRequest request) {
        if (request.timeoutSeconds() != null && request.timeoutSeconds() > 0) {
            return Duration.ofSeconds(request.timeoutSeconds());
        }
        return defaultTimeout;
    }

    private void saveTaskResult(CommandTaskResult result) {
        try {
            redisTemplate.opsForValue().set(taskKey(result.taskId()), objectMapper.writeValueAsString(result), resultTtl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить результат задачи " + result.taskId(), e);
        }
    }

    private CommandGroupStatus buildGroupStatus(String groupTaskId, List<CommandTaskResult> results) {
        int completed = (int) results.stream()
                .filter(r -> r.status() == TaskStatus.SUCCESS || r.status() == TaskStatus.FAILED)
                .count();

        TaskStatus status;
        if (results.stream().anyMatch(r -> r.status() == TaskStatus.FAILED)) {
            status = completed == results.size() ? TaskStatus.FAILED : TaskStatus.RUNNING;
        } else if (completed == results.size()) {
            status = TaskStatus.SUCCESS;
        } else if (results.stream().anyMatch(r -> r.status() == TaskStatus.RUNNING)) {
            status = TaskStatus.RUNNING;
        } else {
            status = TaskStatus.QUEUED;
        }

        return new CommandGroupStatus(groupTaskId, status, results.size(), completed, results);
    }

    private void validateRequest(CommandExecutionRequest request) {
        if (request == null) throw new IllegalArgumentException("Тело запроса обязательно");
        if (!StringUtils.hasText(request.command())) throw new IllegalArgumentException("Команда обязательна");
        if (request.targets() == null || request.targets().isEmpty())
            throw new IllegalArgumentException("Нужно указать хотя бы одно устройство");
        if (request.targets().size() > maxTargets)
            throw new IllegalArgumentException("Слишком много устройств в одном запросе. Максимум: " + maxTargets);
        for (DeviceCommandTarget target : request.targets()) {
            if (target == null) throw new IllegalArgumentException("Описание устройства обязательно");
            if (!StringUtils.hasText(target.host())) throw new IllegalArgumentException("host обязателен");
            if (target.resolvedProtocol() == ConnectionProtocol.WINRM) {
                if (!StringUtils.hasText(target.username()) || !StringUtils.hasText(target.password()))
                    throw new IllegalArgumentException("Для WinRM необходимы username и password");
            }
            if (target.resolvedProtocol() == ConnectionProtocol.SSH) {
                if (!StringUtils.hasText(target.username()))
                    throw new IllegalArgumentException("Для SSH необходим username");
                // password может быть пустым (если ключи)
            }
        }
    }

    private String groupTasksKey(String groupTaskId) {
        return redisKeyPrefix + ":group:" + groupTaskId;
    }

    private String taskKey(String taskId) {
        return redisKeyPrefix + ":task:" + taskId;
    }
}