package com.uniikm.configmanager.integration.adater;

import org.metricshub.winrm.WinRMHttpProtocolEnum;
import org.metricshub.winrm.WindowsRemoteCommandResult;
import org.metricshub.winrm.WindowsRemoteExecutor;
import org.metricshub.winrm.exceptions.WinRMException;
import org.metricshub.winrm.exceptions.WindowsRemoteException;
import org.metricshub.winrm.service.WinRMEndpoint;
import org.metricshub.winrm.service.WinRMService;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;

import com.uniikm.configmanager.common.dto.DeviceCommandTarget;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class WinRMAdapter implements ProtocolAdapter {

     private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final Charset charset;
    private final long timeoutMs;

    private WindowsRemoteExecutor executor;
    private volatile boolean connected = false;
    private final ExecutorService asyncExecutor;

    public WinRMAdapter(String host, int port, String username, String password) {
        this(host, port, username, password, StandardCharsets.UTF_8, 30_000);
    }

    public WinRMAdapter(String host, int port, String username, String password, 
                        Charset charset, long timeoutMs) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
        this.timeoutMs = Math.max(timeoutMs, 5_000); // минимум 5 сек
        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "winrm-adapter-pool");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            if (!isConnected() || executor == null) {
                result.put("success", false);
                result.put("error", "Not connected to the device.");
                return result;
            }

            try {
                // Выполнение команды через официальный API
                WindowsRemoteCommandResult cmdResult = executor.executeCommand(
                        command,          // The command to execute
                        null,             // Working directory (can be null)
                        charset,  // Charset
                        timeoutMs // Timeout
                );

                result.put("stdout", cmdResult.getStdout());
                result.put("stderr", cmdResult.getStderr());
                result.put("exitCode", cmdResult.getStatusCode());
                result.put("success", cmdResult.getStatusCode() == 0);

            } catch (WinRMException e) {
                result.put("success", false);
                result.put("error", describeFailure(e));
                this.connected = false;
            } catch (TimeoutException e) {
                result.put("success", false);
                result.put("error", "Command execution timed out after " + timeoutMs + " ms.");
            } catch (WindowsRemoteException e) {
                result.put("success", false);
                result.put("error", describeFailure(e));
                this.connected = false;
            }

            return result;
        });
    }

    /**
     * Превращает низкоуровневую ошибку WinRM в понятное сообщение. «Authorization
     * loop detected» означает, что хост отклонил NTLM-аутентификацию: сервер
     * повторно отвечает 401, и CXF останавливает цикл повторов. Почти всегда это
     * неверные логин/пароль либо у учётной записи нет прав на WinRM.
     */
    private String describeFailure(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
        if (msg.contains("Authorization loop")) {
            return "WinRM отклонил аутентификацию (" + username + "@" + host + ":" + port + "). "
                 + "Проверьте логин/пароль и что учётной записи разрешён доступ к WinRM "
                 + "(локальная учётка: имя без домена; доменная: DOMAIN\\user). Исходная ошибка: " + msg;
        }
        return "WinRM execution error: " + msg;
    }

    @Override
    public CompletableFuture<Map<String, Object>> connect(DeviceCommandTarget credentials) {
        return CompletableFuture.supplyAsync(() -> {

            Map<String, Object> result = new HashMap<>();

            try {

                WinRMEndpoint endpoint = new WinRMEndpoint(
                    port == 5986
                        ? WinRMHttpProtocolEnum.HTTPS
                        : WinRMHttpProtocolEnum.HTTP,
                    credentials.host() != null ? credentials.host() : host,
                    credentials.port() != null ? credentials.port() : port,
                    credentials.username() != null ? credentials.username() : username,
                    credentials.password() != null ? credentials.password().toCharArray() : password.toCharArray(),
                    null
                );

                this.executor = WinRMService.createInstance(
                    endpoint,
                    timeoutMs,
                    null, // ticketCache
                    Collections.singletonList(AuthenticationEnum.NTLM)
                );

                this.connected = true;

                result.put("success", true);
                result.put("message", "Connected");

            } catch (Exception e) {

                this.connected = false;
                this.executor = null;

                result.put("success", false);
                result.put("error", e.getMessage());
            }

            return result;

        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {

            try {
                if (executor != null) {
                    executor.close();
                }
            } catch (Exception ignored) {
            }

            executor = null;
            connected = false;

        }, asyncExecutor);
    }

    @Override
    public boolean isConnected() {
        return connected && executor != null;
    }

    @Override
    public String getProtocolName() {
        return "WinRM";
    }
}