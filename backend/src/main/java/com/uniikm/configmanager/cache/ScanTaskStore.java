package com.uniikm.configmanager.cache;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Оперативное состояние задач сканирования сети в Redis:
 * <ul>
 *   <li>{@code {prefix}:task:{taskId}} — JSON результата сканирования;</li>
 *   <li>{@code {prefix}:progress:{taskId}} — прогресс в виде «done/total».</li>
 * </ul>
 * Сериализация результата остаётся за вызывающим сервисом; хранилище владеет
 * только ключами, TTL и операциями Redis.
 *
 * <p>Часть модуля {@code cache}: единственного места доступа к Redis.
 */
@Slf4j
@Component
public class ScanTaskStore {

    @Value("${network-scan.redis-key-prefix:network-scan}")
    private String prefix;

    @Value("${network-scan.result-ttl:PT5M}")
    private Duration resultTtl;

    private final StringRedisTemplate redis;

    public ScanTaskStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void saveResultJson(String taskId, String json) {
        redis.opsForValue().set(taskKey(taskId), json, resultTtl);
    }

    /** JSON результата или {@code null}, если задача не найдена/истекла. */
    public String getResultJson(String taskId) {
        return redis.opsForValue().get(taskKey(taskId));
    }

    public void saveProgress(String taskId, int done, int total) {
        try {
            redis.opsForValue().set(progressKey(taskId), done + "/" + total, resultTtl);
        } catch (Exception e) {
            log.debug("Не удалось сохранить прогресс {}: {}", taskId, e.getMessage());
        }
    }

    /** Возвращает [done, total] или {@code null}, если прогресс ещё не записан. */
    public int[] readProgress(String taskId) {
        String v = redis.opsForValue().get(progressKey(taskId));
        if (v == null || !v.contains("/")) return null;
        try {
            String[] p = v.split("/", 2);
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Удаляет результат и прогресс задачи. */
    public void clear(String taskId) {
        redis.delete(taskKey(taskId));
        redis.delete(progressKey(taskId));
    }

    private String taskKey(String taskId) {
        return prefix + ":task:" + taskId;
    }

    private String progressKey(String taskId) {
        return prefix + ":progress:" + taskId;
    }
}
