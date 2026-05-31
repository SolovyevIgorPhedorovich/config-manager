package com.project.configmanager.config.facade;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.configmanager.config.dto.ApplyConfigRequest;
import com.project.configmanager.config.dto.ConfigResponce;
import com.project.configmanager.config.service.ConfigOrchestrationService;
import com.project.configmanager.device.facade.DeviceFacade;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConfigFacadeImpl implements ConfigFacade {
    private final DeviceFacade deviceFacade;
    private final ConfigOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, List<String>> apply(ApplyConfigRequest request) {
        var devices = deviceFacade.getDeviceEntities(request.getDeviceIds());
        JsonNode configNode = objectMapper.valueToTree(request.getConfigData());
        List<String> taskIds = orchestrationService.applyConfiguration(
            devices, 
            configNode,
            request.getDeviceCredentials()  // новая карта
        );
        return Map.of("taskGroupIds", taskIds);
    }
    
    @Override
    public ConfigResponce getAll() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAll'");
    }
    
}
