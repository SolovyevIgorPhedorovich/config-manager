package com.project.configmanager.model.command;

public record DeviceCommandTarget(
    String host,
    Integer port,
    String username,
    String password,
    ConnectionProtocol protocol,
    String community,
    Boolean useSsl,
    Boolean skipCertificateCheck
) {
    public int resolvedPort() {
        if (port != null) {
            return port;
        }
        return resolvedProtocol() == ConnectionProtocol.WINRM ? 5985 : 22;
    }

    public ConnectionProtocol resolvedProtocol() {
        return protocol != null ? protocol : ConnectionProtocol.SSH;
    }

    public boolean resolvedUseSsl() {
        return Boolean.TRUE.equals(useSsl);
    }

    public boolean resolvedSkipCertificateCheck() {
        return Boolean.TRUE.equals(skipCertificateCheck);
    }
}
