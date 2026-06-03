// dto/TerminalSessionRequest.java
package com.uniikm.configmanager.device.dto.terminal;

import com.uniikm.configmanager.common.dto.ConnectionProtocol;

public record TerminalSessionRequest(
    String host,
    Integer port,
    String user,
    String password,
    String community,        // для SNMP
    Boolean useSsl,         // для WinRM
    Boolean skipCertificateCheck, // для WinRM
    ConnectionProtocol protocol // опционально, если автоопределение не подходит
) {}