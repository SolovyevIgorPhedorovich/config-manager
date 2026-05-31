package com.project.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class WindowsConfigCommandGenerator implements ConfigCommandGenerator {

    @Override
    public String generateCommand(JsonNode config) {
        List<String> psCommands = new ArrayList<>();
        psCommands.add("$ErrorActionPreference = 'Stop'");
        
        if (config.has("changeComputerName") && config.get("changeComputerName").asBoolean()) {
            String newName = config.get("newComputerName").asText();
            psCommands.add("Rename-Computer -NewName '" + newName + "' -Force");
        }
        
        if (config.has("timezone")) {
            String tz = config.get("timezone").asText();
            psCommands.add("Set-TimeZone -Id '" + tz + "'");
        }
        
        if (config.has("ntpServer")) {
            String ntp = config.get("ntpServer").asText();
            psCommands.add("w32tm /config /manualpeerlist:\"" + ntp + "\" /syncfromflags:manual /reliable:YES /update");
            psCommands.add("Restart-Service w32time");
            psCommands.add("w32tm /resync");
        }
        
        if (config.has("rdpEnabled") && config.get("rdpEnabled").asBoolean()) {
            psCommands.add("Set-ItemProperty -Path 'HKLM:\\System\\CurrentControlSet\\Control\\Terminal Server' -Name 'fDenyTSConnections' -Value 0");
            psCommands.add("Enable-NetFirewallRule -DisplayGroup 'Remote Desktop'");
        }
        
        if (config.has("firewallEnabled") && !config.get("firewallEnabled").asBoolean()) {
            psCommands.add("Set-NetFirewallProfile -All -Enabled False");
        }
        
        if (config.has("executionPolicy")) {
            String policy = config.get("executionPolicy").asText();
            psCommands.add("Set-ExecutionPolicy " + policy + " -Force");
        }
        
        if (config.has("autoLogonEnabled") && config.get("autoLogonEnabled").asBoolean()) {
            String user = config.get("autoLogonUser").asText();
            String password = config.get("autoLogonPassword").asText();
            psCommands.add("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon' -Name 'AutoAdminLogon' -Value 1");
            psCommands.add("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon' -Name 'DefaultUserName' -Value '" + user + "'");
            psCommands.add("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon' -Name 'DefaultPassword' -Value '" + password + "'");
        }
        
        if (config.has("inactivityTimeout")) {
            int timeout = config.get("inactivityTimeout").asInt();
            psCommands.add("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System' -Name 'InactivityTimeoutSecs' -Value " + (timeout * 60));
        }
        
        psCommands.add("Write-Host 'Configuration applied successfully'");
        
        // Объединяем команды в один скрипт
        String script = String.join("; ", psCommands);
        
        // Оборачиваем в вызов PowerShell
        return "powershell.exe -NoProfile -ExecutionPolicy Bypass -Command \"" + script + "\"";
    }
}