package com.uniikm.configmanager.configuration;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring Boot 4 / Spring 7 по умолчанию сериализует HTTP-ответы через Jackson 3
 * (tools.jackson). Но весь код проекта построен на Jackson 2
 * (com.fasterxml.jackson): JSON-поля сущностей через @JdbcTypeCode, zjsonpatch,
 * аннотации @JsonAlias/@JsonInclude в DTO и бин ObjectMapper.
 *
 * Из-за рассинхрона Jackson-2 JsonNode (например, configData в истории конфигов)
 * не распознавался Jackson-3 конвертером и сериализовался как bean — наружу
 * вылезали служебные геттеры узла (array/object/nodeType/containerNode...),
 * а сам конфиг терялся. Также Jackson 3 игнорировал Jackson-2 аннотации DTO.
 *
 * Поэтому переводим JSON-конвертер MVC на Jackson 2 с нашим ObjectMapper —
 * это выравнивает веб-слой со всем остальным кодом.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    public WebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // MappingJackson2HttpMessageConverter в Spring 7 помечен deprecated (курс на
    // Jackson 3). Осознанный временный компромисс: проект целиком на Jackson 2,
    // полная миграция DTO/Hibernate/zjsonpatch на Jackson 3 — отдельная задача.
    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Убираем Jackson-3 JSON-конвертер и любой ранее добавленный Jackson-2,
        // ставим единственный Jackson-2 конвертер с нашим ObjectMapper первым.
        converters.removeIf(c -> c instanceof JacksonJsonHttpMessageConverter
                              || c instanceof MappingJackson2HttpMessageConverter);
        converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper));
    }
}
