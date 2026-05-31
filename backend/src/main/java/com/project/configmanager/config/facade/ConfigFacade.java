package com.project.configmanager.config.facade;

import java.util.List;
import java.util.Map;

import com.project.configmanager.config.dto.ApplyConfigRequest;
import com.project.configmanager.config.dto.ConfigResponce;

public interface ConfigFacade {
    public Map<String, List<String>> apply(ApplyConfigRequest request);

    public ConfigResponce getAll();
}
