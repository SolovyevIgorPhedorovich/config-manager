package com.project.configmanager.api;

import com.project.configmanager.config.dto.ApplyConfigRequest;
import com.project.configmanager.config.dto.ConfigApplyStatus;
import com.project.configmanager.config.facade.ConfigFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigFacade configFacade;
    private final RedisTemplate<String, Object> redisTemplate;   // добавлено

    @PostMapping("/devices/configure")
    public ResponseEntity<?> configureDevices(@Validated @RequestBody ApplyConfigRequest request) {
        return ResponseEntity.ok(configFacade.apply(request));
    }

    @GetMapping("/devices/{deviceId}/config-apply/status")
    public ResponseEntity<ConfigApplyStatus> getApplyStatus(@PathVariable Long deviceId) {
        String groupTaskId = (String) redisTemplate.opsForValue().get("device:current-task:" + deviceId);
        if (groupTaskId == null) {
            return ResponseEntity.notFound().build();
        }

        Map<Object, Object> data = redisTemplate.opsForHash().entries("config:apply:" + groupTaskId);
        if (data.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ConfigApplyStatus status = new ConfigApplyStatus(
            (String) data.get("status"),
            (String) data.get("startedAt"),
            (String) data.get("finishedAt"),
            (String) data.get("errorMessage"),
            null   // прогресс не используется, можно передать null
        );
        return ResponseEntity.ok(status);
    }
}