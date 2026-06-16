package com.uniikm.configmanager.integration.adapter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.uniikm.configmanager.common.dto.DeviceCommandTarget;

public interface ProtocolAdapter {
        
    String getProtocolName();

    CompletableFuture<Map<String, Object>> executeCommand(String command);

    CompletableFuture<Map<String, Object>> connect(DeviceCommandTarget credentials);

    CompletableFuture<Void> disconnect();

    boolean isConnected();
}
