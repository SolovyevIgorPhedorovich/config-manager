package com.project.configmanager.model.device;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeviceInput(
    String hostname,
    //@JsonProperty("ip")
    List<String> ips,
    Integer type,
    Long osVersionId,
    String groupName,
    Boolean isActive
) {}

