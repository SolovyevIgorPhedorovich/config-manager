package com.project.configmanager.integration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ProtocolAdapter {
        
    String getProtocolName();

    CompletableFuture<Map<String, Object>> executeCommand(String command);

    CompletableFuture<Map<String, Object>> connect(Map<String, String> credentials);

    CompletableFuture<Void> disconnect();

    boolean isConnected();
}
