package com.uniikm.configmanager.common.terminal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface RemoteConnection {
    CompletableFuture<Map<String, Object>> connect();
    CompletableFuture<Map<String, Object>> executeCommand(String command);
    void disconnect();
}