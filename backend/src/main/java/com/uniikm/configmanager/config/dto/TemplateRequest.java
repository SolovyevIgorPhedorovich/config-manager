package com.uniikm.configmanager.config.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private JsonNode content;
}
