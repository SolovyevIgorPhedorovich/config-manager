package com.uniikm.configmanager.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Превращает конкретную конфигурацию устройства в шаблон, заменяя уникальные
 * в сети значения на переменные ({@code {{device.hostname}}}, {@code {{device.ip}}}),
 * чтобы шаблон можно было применять к разным устройствам без конфликтов имён/адресов.
 *
 * Подстановка этих переменных под конкретное устройство выполняется при применении
 * шаблона в {@code TemplateService.renderContent}.
 */
@Component
public class TemplateParameterizer {

    /** Ключи, чьё значение — имя устройства. */
    private static final Set<String> HOSTNAME_KEYS = Set.of("hostname", "devicename");

    /** Ключи, чьё IPv4-значение — адрес самого устройства. */
    private static final Set<String> IP_KEYS = Set.of(
            "ip", "ipaddress", "gateway", "defaultgateway", "primaryip", "managementip");

    private static final String VAR_HOSTNAME = "{{device.hostname}}";
    private static final String VAR_IP = "{{device.ip}}";

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");

    /** Возвращает параметризованную копию конфигурации (исходник не меняется). */
    public JsonNode parameterize(JsonNode config) {
        if (config == null) return null;
        JsonNode copy = config.deepCopy();
        walk(copy);
        return copy;
    }

    private void walk(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.fieldNames().forEachRemaining(field -> {
                JsonNode value = obj.get(field);
                if (value != null && value.isValueNode() && value.isTextual()) {
                    String replaced = replaceValue(field, value.asText());
                    if (replaced != null) obj.put(field, replaced);
                } else {
                    walk(value);
                }
            });
        } else if (node instanceof ArrayNode arr) {
            for (JsonNode item : arr) walk(item);
        }
    }

    /** Возвращает значение-переменную либо null, если поле менять не нужно. */
    private String replaceValue(String key, String value) {
        String k = key.toLowerCase();

        if (HOSTNAME_KEYS.contains(k)) {
            return VAR_HOSTNAME;
        }
        // Маски подсети не трогаем (255.255.* и поля с mask/subnet в имени).
        boolean maskLike = k.contains("mask") || k.contains("subnet") || value.startsWith("255.");
        if (!maskLike && IPV4.matcher(value).matches()) {
            // IP-адрес самого устройства → переменная; прочие IP (next-hop, серверы)
            // тоже заменяем на device.ip только если ключ из IP_KEYS, иначе оставляем.
            if (IP_KEYS.contains(k)) return VAR_IP;
        }
        return null;
    }
}
