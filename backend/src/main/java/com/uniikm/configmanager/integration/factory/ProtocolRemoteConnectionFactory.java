package com.uniikm.configmanager.integration.factory;

import com.uniikm.configmanager.common.terminal.RemoteConnection;
import com.uniikm.configmanager.common.terminal.RemoteConnectionFactory;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.integration.adater.ProtocolAdapter;
import com.uniikm.configmanager.integration.factory.ProtocolAdapterFactory;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ProtocolRemoteConnectionFactory implements RemoteConnectionFactory {
    
    private final ProtocolAdapterFactory adapterFactory;
    
    @Override
    public RemoteConnection create(DeviceCommandTarget target) {
        ProtocolAdapter adapter = adapterFactory.create(target);
        return new ProtocolRemoteConnection(adapter);
    }
    
    private static class ProtocolRemoteConnection implements RemoteConnection {
        private final ProtocolAdapter adapter;
        
        ProtocolRemoteConnection(ProtocolAdapter adapter) {
            this.adapter = adapter;
        }
        
        @Override
        public CompletableFuture<Map<String, Object>> connect() {
            return adapter.connect(null);
        }
        
        @Override
        public CompletableFuture<Map<String, Object>> executeCommand(String command) {
            return adapter.executeCommand(command);
        }
        
        @Override
        public void disconnect() {
            try {
                adapter.disconnect();
            } catch (Exception e) {
                // Логирование
            }
        }
    }
}