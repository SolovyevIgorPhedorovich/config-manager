package com.uniikm.configmanager.common.terminal;

import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface RemoteConnectionFactory {
    RemoteConnection create(DeviceCommandTarget target);
}