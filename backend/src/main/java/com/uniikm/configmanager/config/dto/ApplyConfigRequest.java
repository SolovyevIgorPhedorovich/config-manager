package com.uniikm.configmanager.config.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

@Data
public class ApplyConfigRequest {
    private List<Long> deviceIds;
    private String channel;
    private String target;
    private Map<String, Object> configData;
    // Фронт присылает это поле под именем "credentials" — принимаем оба имени.
    @JsonAlias("credentials")
    private Map<Long, DeviceCredentials> deviceCredentials;
}