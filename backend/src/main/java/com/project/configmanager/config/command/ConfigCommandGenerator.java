package com.project.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;

public interface ConfigCommandGenerator {
    
    public String generateCommand(JsonNode config);
    
}
