package com.project.configmanager.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ssh")
public class SSHController {

    @Value("${ssh.proxy.enabled:true}") // настройка в application.yml
    private boolean proxyEnabled;

    @PostMapping("/connect")
    public ResponseEntity<Map<String, String>> connectSSH(
            @RequestParam String host,
            @RequestParam(defaultValue = "22") int port,
            @RequestParam String user) {
        
        Map<String, String> response = new HashMap<>();
        
        if (!proxyEnabled) {
            response.put("status", "error");
            response.put("message", "SSH через браузер отключён администратором");
            return ResponseEntity.badRequest().body(response);
        }

        // Добавить
        // 1. Проверка прав пользователя (JWT)
        // 2. Генерация временного SSH-ключа
        // 3. Проксирование через Spring WebSocket или SSE

        response.put("status", "success");
        response.put("message", "Подключение успешно инициировано (требуется backend)");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute")
    public ResponseEntity<String> executeCommand(
            @RequestBody Map<String, String> request) {
        
        String host = request.get("host");
        String command = request.get("command");

        if (command == null || command.isEmpty()) {
            return ResponseEntity.badRequest().body("Команда не указана");
        }

        // Добавить
        // - White-list разрешённых команд
        // - Docker-контейнеры для изоляции
        // - Аудит всех действий

        try {
            ProcessBuilder pb = new ProcessBuilder("ssh", host, command);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            return ResponseEntity.ok(exitCode == 0 ? output.toString() : "Ошибка выполнения");

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Ошибка выполнения команды: " + e.getMessage());
        }
    }
}
