package com.uniikm.configmanager.config.facade;


import java.util.List;

import org.springframework.http.ResponseEntity;

import com.uniikm.configmanager.config.dto.ApplyConfigRequest;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.ConfigCompareRequest;
import com.uniikm.configmanager.config.dto.ConfigCompareResponse;
import com.uniikm.configmanager.config.dto.ConfigHistoryResponse;
import com.uniikm.configmanager.config.dto.ConfigStatusResponse;
import com.uniikm.configmanager.integration.dto.DeviceProbeResult;

public interface ConfigFacade {

    ApplyConfigResponse apply(ApplyConfigRequest request);

    ConfigHistoryResponse getHistory(Long deviceId);

    ConfigCompareResponse compare(ConfigCompareRequest request);

    List<ConfigStatusResponse> getStatus(String taskGroupId);

    /** Фактический статус применения одной группы задач (config:apply:&lt;groupTaskId&gt;). */
    ConfigStatusResponse getApplyGroupStatus(String groupTaskId);

    /**
     * Захват фактической конфигурации устройства по результату зондирования и
     * сверка с сохранённой активной версией (фиксация расхождения — drift).
     * Контракт для модуля устройств, вызывается после сканирования сети.
     */
    void captureAndReconcile(Long deviceId, DeviceProbeResult probe);
}