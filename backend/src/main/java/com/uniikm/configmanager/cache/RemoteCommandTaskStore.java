package com.uniikm.configmanager.cache;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Оперативное состояние задач удалённого выполнения команд в Redis:
 * <ul>
 *   <li>{@code {prefix}:task:{taskId}} — JSON результата отдельной задачи;</li>
 *   <li>{@code {prefix}:group:{groupTaskId}} — список taskId, входящих в группу.</li>
 * </ul>
 * Сериализация результата остаётся за вызывающим сервисом; хранилище владеет
 * только ключами, TTL и операциями Redis.
 *
 * <p>Часть модуля {@code cache}: единственного места доступа к Redis.
 */
@Component
public class RemoteCommandTaskStore {

    @Value("${remote-command.redis-key-prefix:remote-command}")
    private String prefix;

    @Value("${remote-command.result-ttl:PT24H}")
    private Duration resultTtl;

    private final StringRedisTemplate redis;

    public RemoteCommandTaskStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void saveTaskJson(String taskId, String json) {
        redis.opsForValue().set(taskKey(taskId), json, resultTtl);
    }

    /** JSON результата или {@code null}, если задача не найдена/истекла. */
    public String getTaskJson(String taskId) {
        return redis.opsForValue().get(taskKey(taskId));
    }

    public void addTaskToGroup(String groupTaskId, String taskId) {
        redis.opsForList().rightPush(groupTasksKey(groupTaskId), taskId);
    }

    public void expireGroup(String groupTaskId) {
        redis.expire(groupTasksKey(groupTaskId), resultTtl);
    }

    public List<String> getGroupTaskIds(String groupTaskId) {
        return redis.opsForList().range(groupTasksKey(groupTaskId), 0, -1);
    }

    private String groupTasksKey(String groupTaskId) {
        return prefix + ":group:" + groupTaskId;
    }

    private String taskKey(String taskId) {
        return prefix + ":task:" + taskId;
    }
}
