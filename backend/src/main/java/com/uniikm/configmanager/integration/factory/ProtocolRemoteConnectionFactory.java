package com.uniikm.configmanager.integration.factory;

import com.uniikm.configmanager.common.terminal.RemoteConnection;
import com.uniikm.configmanager.common.terminal.RemoteConnectionFactory;
import com.uniikm.configmanager.common.terminal.SshShellConnection;
import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.integration.adapter.ProtocolAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ProtocolRemoteConnectionFactory implements RemoteConnectionFactory {

    private final ProtocolAdapterFactory adapterFactory;

    @Value("${ssh.connect-timeout-ms:30000}")
    private int sshConnectTimeoutMs;

    @Override
    public RemoteConnection create(DeviceCommandTarget target) {
        // SSH-терминал интерактивный: долгоживущий shell-канал с PTY и стримингом.
        if (target.protocol() == ConnectionProtocol.SSH) {
            return new SshShellConnection(target, sshConnectTimeoutMs);
        }
        // WinRM/SNMP — модель «команда → ответ».
        ProtocolAdapter adapter = adapterFactory.create(target);
        return new ProtocolRemoteConnection(adapter, target);
    }

    /** Неинтерактивная обёртка над {@link ProtocolAdapter} (WinRM/SNMP). */
    private static class ProtocolRemoteConnection implements RemoteConnection {
        private final ProtocolAdapter adapter;
        private final DeviceCommandTarget target;

        ProtocolRemoteConnection(ProtocolAdapter adapter, DeviceCommandTarget target) {
            this.adapter = adapter;
            this.target = target;
        }

        @Override
        public CompletableFuture<Map<String, Object>> connect() {
            // Передаём реальную цель (раньше шёл null → NPE в адаптерах).
            return adapter.connect(target);
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
                // best-effort
            }
        }
    }
}
