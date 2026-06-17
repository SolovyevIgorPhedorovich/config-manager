package com.uniikm.configmanager.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Оперативное состояние применения конфигураций в Redis:
 * <ul>
 *   <li>{@code config:apply:{groupTaskId}} — хэш со статусом задачи применения;</li>
 *   <li>{@code config:batch{batchId}} — список groupTaskId, входящих в пакет;</li>
 *   <li>{@code device:current-task:{deviceId}} — текущая задача устройства.</li>
 * </ul>
 * Все записи живут с TTL {@link #TTL} и истекают сами.
 *
 * <p>Часть модуля {@code cache}: единственного места доступа к Redis.
 */
@Component
public class ConfigApplyStatusStore {

    private static final Duration TTL = Duration.ofHours(1);
    private static final String APPLY_PREFIX = "config:apply:";
    private static final String BATCH_PREFIX = "config:batch:";
    private static final String CURRENT_TASK_PREFIX = "device:current-task:";

    private final RedisTemplate<String, Object> redis;

    public ConfigApplyStatusStore(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    // ── Статус задачи применения (хэш config:apply:{groupTaskId}) ───────────────

    /** Фиксирует старт задачи применения (статус IN_PROGRESS) до запуска выполнения. */
    public void initApplyStatus(String groupTaskId, Long deviceId, Long configVersionId) {
        String key = APPLY_PREFIX + groupTaskId;
        redis.opsForHash().putAll(key, Map.of(
                "deviceId", deviceId,
                "configVersionId", configVersionId,
                "status", "IN_PROGRESS",
                "startedAt", Instant.now().toString()
        ));
        redis.expire(key, TTL);
    }

    /** Завершает задачу применения: проставляет финальный статус, время и (опц.) ошибку. */
    public void completeApplyStatus(String groupTaskId, String status, String error) {
        String key = APPLY_PREFIX + groupTaskId;
        redis.opsForHash().put(key, "status", status);
        redis.opsForHash().put(key, "finishedAt", Instant.now().toString());
        if (error != null) {
            redis.opsForHash().put(key, "errorMessage", error);
        }
    }

    public Long getDeviceId(String groupTaskId) {
        return asLong(redis.opsForHash().get(APPLY_PREFIX + groupTaskId, "deviceId"));
    }

    public Long getConfigVersionId(String groupTaskId) {
        return asLong(redis.opsForHash().get(APPLY_PREFIX + groupTaskId, "configVersionId"));
    }

    /** Текущий статус задачи применения; {@code Optional.empty()}, если ключа нет/истёк. */
    public Optional<ConfigApplyStatusView> getApplyStatus(String groupTaskId) {
        Map<Object, Object> h = redis.opsForHash().entries(APPLY_PREFIX + groupTaskId);
        if (h == null || h.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ConfigApplyStatusView(
                asStr(h.get("status")),
                asLong(h.get("deviceId")),
                asStr(h.get("startedAt")),
                asStr(h.get("finishedAt")),
                asStr(h.get("errorMessage"))
        ));
    }

    // ── Текущая задача устройства (device:current-task:{deviceId}) ──────────────

    public void setCurrentTask(Long deviceId, String groupTaskId) {
        redis.opsForValue().set(CURRENT_TASK_PREFIX + deviceId, groupTaskId, TTL);
    }

    public void clearCurrentTask(Long deviceId) {
        redis.delete(CURRENT_TASK_PREFIX + deviceId);
    }

    // ── Пакет применения (список groupTaskId) ───────────────────────────────────

    public void saveBatchTaskIds(String batchId, List<String> groupTaskIds) {
        String key = BATCH_PREFIX + batchId;
        redis.opsForList().rightPushAll(key, groupTaskIds.toArray());
        redis.expire(key, TTL);
    }

    public List<Object> getBatchTaskIds(String batchId) {
        return redis.opsForList().range(BATCH_PREFIX + batchId, 0, -1);
    }

    private static String asStr(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
