package com.uniikm.configmanager.integration.dto;

public record ScanConfig(
        int snmpPort,
        String community,
        String snmpVersion,
        // SNMPv3 (USM). Для v1/v2c эти поля игнорируются.
        String securityName,   // логин (security name)
        String authProtocol,   // "SHA" | "SHA256" | "MD5" | null
        String authPassword,   // пароль аутентификации
        String privProtocol,   // "AES" | "AES256" | "DES" | null
        String privPassword,   // пароль шифрования
        // SSH / WinRM
        String sshUsername,
        String sshPassword,
        String winrmUsername,
        String winrmPassword,
        boolean pingEnabled
) {
    public boolean hasSsh()   { return sshUsername   != null && !sshUsername.isBlank(); }
    public boolean hasWinRM() { return winrmUsername != null && !winrmUsername.isBlank(); }

    public boolean isV3()      { return "v3".equalsIgnoreCase(snmpVersion); }
    public boolean hasV3Auth() { return authPassword != null && !authPassword.isBlank(); }
    public boolean hasV3Priv() { return privPassword != null && !privPassword.isBlank(); }
}
