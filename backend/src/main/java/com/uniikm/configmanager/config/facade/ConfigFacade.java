package com.uniikm.configmanager.config.facade;


import java.util.List;

import org.springframework.http.ResponseEntity;

import com.uniikm.configmanager.config.dto.ApplyConfigRequest;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.ConfigCompareRequest;
import com.uniikm.configmanager.config.dto.ConfigCompareResponse;
import com.uniikm.configmanager.config.dto.ConfigHistoryResponse;
import com.uniikm.configmanager.config.dto.ConfigStatusResponse;

public interface ConfigFacade {

    ApplyConfigResponse apply(ApplyConfigRequest request);

    ConfigHistoryResponse getHistory(Long deviceId);

    ConfigCompareResponse compare(ConfigCompareRequest request);

    List<ConfigStatusResponse> getStatus(String taskGroupId);
}