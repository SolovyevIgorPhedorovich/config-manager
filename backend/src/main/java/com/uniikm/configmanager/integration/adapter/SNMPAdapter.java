package com.uniikm.configmanager.integration.adapter;

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
            StringBuilder stdout = new StringBuilder();
            boolean hasError = false;

            for (String line : command.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                try {
                    if (line.startsWith("SET ")) {
                        // SET <oid> <type> <value>
                        String[] parts = line.split(" ", 4);
                        if (parts.length < 4) {
                            stdout.append("ERROR: invalid SET format: ").append(line).append("\n");
                            hasError = true;
                            continue;
                        }
                        Map<String, String> r = client.set(parts[1], parts[2], parts[3]);
                        if (r.containsKey("error")) {
                            stdout.append("ERROR ").append(parts[1]).append(": ").append(r.get("error")).append("\n");
                            hasError = true;
                        } else {
                            stdout.append("OK ").append(parts[1]).append(" = ").append(parts[3]).append("\n");
                        }
                    } else {
                        String oid = line.startsWith("GET ") ? line.substring(4).trim() : line;
                        Map<String, String> r = client.get(oid);
                        if (r.containsKey("error")) {
                            stdout.append("ERROR ").append(oid).append(": ").append(r.get("error")).append("\n");
                            hasError = true;
                        } else {
                            r.forEach((k, v) -> stdout.append(k).append(" = ").append(v).append("\n"));
                        }
                    }
                } catch (Exception e) {
                    stdout.append("ERROR: ").append(e.getMessage()).append("\n");
                    hasError = true;
                }
            }

            result.put("success", !hasError);
            result.put("stdout", stdout.toString());
            if (hasError) result.put("stderr", "One or more SNMP operations failed");
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