package com.uniikm.configmanager.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Тесты конфигурации сериализации JSON. Ключевая проверка — устойчивость
 * десериализации к «лишним» полям в теле запроса: именно из-за падения на
 * неизвестном поле authType фронт получал HTTP 400 на логине.
 */
class JacksonConfigTest {

    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    record LoginRequest(String username, String password) {}

    @Test
    @DisplayName("Неизвестное поле в JSON игнорируется (фикс 400 на логине)")
    void ignoresUnknownProperties() {
        String json = "{\"username\":\"admin\",\"password\":\"secret\",\"authType\":\"DB\"}";

        assertThatCode(() -> mapper.readValue(json, LoginRequest.class))
                .doesNotThrowAnyException();

        LoginRequest req = readSafely(json);
        assertThat(req.username()).isEqualTo("admin");
        assertThat(req.password()).isEqualTo("secret");
    }

    @Test
    @DisplayName("Известные поля десериализуются корректно")
    void deserializesKnownProperties() {
        LoginRequest req = readSafely("{\"username\":\"u\",\"password\":\"p\"}");
        assertThat(req.username()).isEqualTo("u");
        assertThat(req.password()).isEqualTo("p");
    }

    @Test
    @DisplayName("Даты сериализуются в ISO-8601, а не как timestamp")
    void writesDatesAsIso() throws Exception {
        String out = mapper.writeValueAsString(OffsetDateTime.parse("2026-06-09T10:00:00+02:00"));
        assertThat(out).contains("2026-06-09T10:00:00");
        assertThat(out).doesNotContain("1.7"); // не числовой timestamp
    }

    private LoginRequest readSafely(String json) {
        try {
            return mapper.readValue(json, LoginRequest.class);
        } catch (Exception e) {
            throw new AssertionError("Десериализация не должна падать: " + e.getMessage(), e);
        }
    }
}
