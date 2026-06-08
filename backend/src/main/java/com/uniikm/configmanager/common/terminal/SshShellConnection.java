package com.uniikm.configmanager.common.terminal;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Интерактивное SSH-соединение для веб-терминала: открывает один долгоживущий
 * shell-канал с PTY, держит его открытым, пишет нажатия клавиш в stdin канала и
 * стримит вывод (stdout/stderr PTY) обратно через слушателя.
 *
 * В отличие от {@code SSHAdapter} (ChannelExec, одна команда на канал), здесь
 * сохраняется состояние сессии: история, редактирование строки, локальное эхо
 * PTY, интерактивные программы (vi, top, и т.п.) и Cisco IOS CLI.
 */
@Slf4j
public class SshShellConnection implements RemoteConnection {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final int connectTimeoutMs;

    private Session session;
    private ChannelShell channel;
    private OutputStream channelIn;   // сюда пишем нажатия клавиш
    private Thread readerThread;

    private volatile Consumer<String> outputListener;
    // Буфер вывода, пришедшего до того, как подписался слушатель (например, приглашение shell)
    private final StringBuilder pending = new StringBuilder();
    private final Object outputLock = new Object();
    private volatile boolean closed = false;

    private int cols = 80;
    private int rows = 24;

    public SshShellConnection(DeviceCommandTarget target, int connectTimeoutMs) {
        this.host = target.host();
        this.port = target.port() != null ? target.port() : 22;
        this.username = target.username();
        this.password = target.password();
        this.connectTimeoutMs = connectTimeoutMs;
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public CompletableFuture<Map<String, Object>> connect() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, host, port);
                if (password != null) {
                    session.setPassword(password);
                }
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.setTimeout(connectTimeoutMs);
                session.connect(connectTimeoutMs);

                channel = (ChannelShell) session.openChannel("shell");
                channel.setPtyType("xterm-256color", cols, rows, 0, 0);
                channelIn = channel.getOutputStream();
                InputStream channelOut = channel.getInputStream();
                channel.connect(connectTimeoutMs);

                startReader(channelOut);

                result.put("success", true);
                result.put("message", "SSH shell established to " + username + "@" + host + ":" + port);
            } catch (Exception e) {
                log.warn("SSH shell connect failed for {}@{}:{} — {}", username, host, port, e.getMessage());
                result.put("success", false);
                result.put("error", e.getMessage());
                // Буферизуем ошибку: она уйдёт в терминал, как только подпишется WebSocket.
                emit("\r\n[31mОшибка подключения: " + e.getMessage() + "[0m\r\n");
            }
            return result;
        });
    }

    private void startReader(InputStream channelOut) {
        readerThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                int n;
                while (!closed && (n = channelOut.read(buffer)) != -1) {
                    emit(new String(buffer, 0, n, StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                if (!closed) {
                    log.debug("SSH shell reader stopped for {}: {}", host, e.getMessage());
                }
            } finally {
                emit("\r\n[31m[сессия завершена][0m\r\n");
            }
        }, "ssh-shell-reader-" + host);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void emit(String text) {
        Consumer<String> listener;
        synchronized (outputLock) {
            listener = outputListener;
            if (listener == null) {
                pending.append(text);
                return;
            }
        }
        listener.accept(text);
    }

    @Override
    public void onOutput(Consumer<String> listener) {
        String buffered;
        synchronized (outputLock) {
            this.outputListener = listener;
            buffered = pending.length() > 0 ? pending.toString() : null;
            pending.setLength(0);
        }
        if (buffered != null && listener != null) {
            listener.accept(buffered);
        }
    }

    @Override
    public void write(String data) {
        if (data == null || data.isEmpty()) return;
        try {
            channelIn.write(data.getBytes(StandardCharsets.UTF_8));
            channelIn.flush();
        } catch (Exception e) {
            log.debug("SSH shell write failed for {}: {}", host, e.getMessage());
        }
    }

    @Override
    public void resize(int cols, int rows) {
        if (cols <= 0 || rows <= 0) return;
        this.cols = cols;
        this.rows = rows;
        if (channel != null && channel.isConnected()) {
            channel.setPtySize(cols, rows, 0, 0);
        }
    }

    /**
     * Для интерактивной сессии «выполнить команду» = напечатать её и нажать Enter.
     * Вывод придёт асинхронно через слушателя, поэтому возвращаем пустой успех.
     */
    @Override
    public CompletableFuture<Map<String, Object>> executeCommand(String command) {
        write(command.endsWith("\n") ? command : command + "\n");
        return CompletableFuture.completedFuture(Map.of("success", true));
    }

    @Override
    public void disconnect() {
        closed = true;
        try {
            if (readerThread != null) readerThread.interrupt();
        } catch (Exception ignored) {}
        try {
            if (channel != null && channel.isConnected()) channel.disconnect();
        } catch (Exception ignored) {}
        try {
            if (session != null && session.isConnected()) session.disconnect();
        } catch (Exception ignored) {}
    }
}
