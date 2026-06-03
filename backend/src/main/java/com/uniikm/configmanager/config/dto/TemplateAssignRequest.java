package com.uniikm.configmanager.config.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TemplateAssignRequest {

    @NotEmpty
    private List<Long> deviceIds;
}
