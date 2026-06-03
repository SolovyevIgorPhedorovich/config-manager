package com.uniikm.configmanager.integration.adater;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.integration.client.SnmpClient;

public class SNMPAdapter implements ProtocolAdapter {

    private final SnmpClient client;

    public SNMPAdapter(SnmpClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Map<String, Object>> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            try {
                result.putAll(client.get(command));
                result.put("success", true);
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> connect(DeviceCommandTarget credentials) {
        return CompletableFuture.supplyAsync(() -> {
            client.connect(
                credentials.host(),
                credentials.port() != null ? credentials.port() : 161,
                credentials.community()
            );
            return Map.of("success", true);
        });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(client::close);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public String getProtocolName() {
        return "SNMP";
    }
}