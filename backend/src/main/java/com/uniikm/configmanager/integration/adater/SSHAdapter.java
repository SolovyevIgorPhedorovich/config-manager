package com.uniikm.configmanager.integration.adater;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jcraft.jsch.Session;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;

import java.io.InputStream;
import java.util.HashMap;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

public class SSHAdapter implements ProtocolAdapter {
    
    private String host;
    private int port;
    private String username;
    private String password;
    private String privateKeyPath;
    
    private Session session;
    private JSch jsch;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public SSHAdapter() {
        this.port = 22;
    }

    public SSHAdapter(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public CompletableFuture<Map<String, Object>> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            if (!isConnected()) {
                result.put("error", "Not connected to device");
                result.put("success", false);
                return result;
            }
            
            try {
                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(command);
                
                InputStream in = channel.getInputStream();
                InputStream err = channel.getErrStream();
                
                channel.connect(30000); // 30 seconds timeout
                
                StringBuilder output = new StringBuilder();
                StringBuilder errorOutput = new StringBuilder();
                
                byte[] buffer = new byte[1024];
                while (true) {
                    while (in.available() > 0) {
                        int len = in.read(buffer);
                        if (len > 0) {
                            output.append(new String(buffer, 0, len));
                        }
                    }
                    while (err.available() > 0) {
                        int len = err.read(buffer);
                        if (len > 0) {
                            errorOutput.append(new String(buffer, 0, len));
                        }
                    }
                    if (channel.isClosed()) {
                        break;
                    }
                    Thread.sleep(100);
                }
                
                int exitCode = channel.getExitStatus();
                channel.disconnect();
                
                result.put("stdout", output.toString());
                result.put("stderr", errorOutput.toString());
                result.put("exitCode", exitCode);
                result.put("success", exitCode == 0);
                
            } catch (Exception e) {
                result.put("error", e.getMessage());
                result.put("success", false);
            }
            
            return result;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> connect(DeviceCommandTarget credentials) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                this.host = credentials.host() != null ? credentials.host() : host;
                this.port = credentials.port() != null ? credentials.port() : port;
                this.username = credentials.username() != null ? credentials.username() : username;
                this.password = credentials.password() != null ? credentials.password() : password;
                
                jsch = new JSch();
                
                if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
                    jsch.addIdentity(privateKeyPath);
                }
                
                session = jsch.getSession(username, host, port);
                
                if (password != null && !password.isEmpty()) {
                    session.setPassword(password);
                }
                
                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                
                session.setTimeout(30000);
                session.connect(30000);
                
                connected.set(true);
                result.put("success", true);
                result.put("message", "SSH connection established to " + username + "@" + host + ":" + port);
                
            } catch (JSchException e) {
                connected.set(false);
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            connected.set(false);
        });
    }

    @Override
    public boolean isConnected() {
        return connected.get() && session != null && session.isConnected();
    }
    
    @Override
    public String getProtocolName() {
        return "SSH";
    }
}
