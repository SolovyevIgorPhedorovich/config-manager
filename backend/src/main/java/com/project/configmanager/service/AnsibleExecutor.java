// AnsibleExecutor.java
package com.project.configmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnsibleExecutor {

    public String deployWindowsConfig(String targetIp, String newHostname) {
        log.info("🚀 Starting deployment to Windows PC: {} with hostname={}", targetIp, newHostname);

        try {
            String inventoryContent = String.format("""
                    [windows_pcs]
                    %s ansible_host=%s ansible_user=Administrator ansible_password=Passw0rd! ansible_connection=winrm ansible_winrm_server_cert_validation=ignore
                    """, targetIp, targetIp);

            Path tempInventory = Files.createTempFile("inventory_", ".yaml");
            Files.write(tempInventory, inventoryContent.getBytes());

            // Генерируем переменные
            Map<String, Object> extraVars = new HashMap<>();
            extraVars.put("hostname", newHostname);
            String extraVarsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extraVars);

            // Вызываем ansible-playbook
            ProcessBuilder pb = new ProcessBuilder(
                "bash", "-c",
                "cd " + getPlaybookDir() + " && " +
                "ansible-playbook deploy_windows.yml " +
                "--inventory " + tempInventory.toAbsolutePath() +
                " --extra-vars '" + extraVarsJson.replace("\"", "\\\"") + "'"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Ansible] {}", line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            tempInventory.toFile().delete(); // cleanup

            return "Exit code: " + exitCode + "\n" + output;

        } catch (Exception e) {
            log.error("Error running Ansible", e);
            return "ERROR: " + e.getMessage();
        }
    }

    private String getPlaybookDir() {
        try {
            new ClassPathResource("ansible/deploy_windows.yml").getFile().toPath();
            return "src/main/resources/ansible";
        } catch (IOException e) {
            return "./playbooks";  // fallback
        }
    }
}
