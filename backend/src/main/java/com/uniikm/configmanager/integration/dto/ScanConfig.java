package com.uniikm.configmanager.integration.dto;

public record ScanConfig(
        int snmpPort,
        String community,
        String snmpVersion,
        String sshUsername,
        String sshPassword,
        String winrmUsername,
        String winrmPassword,
        boolean pingEnabled
) {
    public boolean hasSsh()   { return sshUsername   != null && !sshUsername.isBlank(); }
    public boolean hasWinRM() { return winrmUsername != null && !winrmUsername.isBlank(); }
}
