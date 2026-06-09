package com.uniikm.configmanager.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrator {

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void migrate() {
        migrateEventLogUserId();
        migrateDeviceConfigPrimaryKey();
    }

    private void migrateEventLogUserId() {
        String dataType = jdbc.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name = 'event_log' AND column_name = 'user_id'",
            String.class
        );
        if ("character varying".equals(dataType)) {
            log.info("Migrating event_log.user_id: VARCHAR -> BIGINT");
            jdbc.execute(
                "ALTER TABLE event_log ALTER COLUMN user_id TYPE BIGINT " +
                "USING NULLIF(TRIM(user_id), '')::BIGINT"
            );
        }
    }

    private void migrateDeviceConfigPrimaryKey() {
        Boolean hasPk = jdbc.queryForObject(
            "SELECT EXISTS (" +
            "  SELECT 1 FROM information_schema.table_constraints " +
            "  WHERE table_schema = 'public' AND table_name = 'device_config' " +
            "  AND constraint_type = 'PRIMARY KEY'" +
            ")",
            Boolean.class
        );
        if (Boolean.FALSE.equals(hasPk)) {
            log.info("Migrating device_config: adding PRIMARY KEY on device_id");
            // Удаляем дубли если есть — оставляем запись с последним applied_at
            jdbc.execute(
                "DELETE FROM device_config d1 " +
                "USING device_config d2 " +
                "WHERE d1.device_id = d2.device_id AND d1.applied_at < d2.applied_at"
            );
            jdbc.execute("ALTER TABLE device_config ADD PRIMARY KEY (device_id)");
        }
    }
}
