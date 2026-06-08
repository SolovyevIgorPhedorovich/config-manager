package com.uniikm.configmanager.common.terminal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface RemoteConnection {
    CompletableFuture<Map<String, Object>> connect();
    CompletableFuture<Map<String, Object>> executeCommand(String command);
    void disconnect();

    /**
     * Интерактивное (PTY) соединение поддерживает потоковый ввод/вывод:
     * нажатия клавиш пишутся через {@link #write(String)}, а вывод приходит
     * асинхронно в слушателя, заданного через {@link #onOutput(Consumer)}.
     * Неинтерактивные соединения (WinRM/SNMP) работают по модели
     * «команда → ответ» через {@link #executeCommand(String)}.
     */
    default boolean isInteractive() {
        return false;
    }

    /** Записать ввод (нажатия клавиш) в интерактивную сессию. */
    default void write(String data) {
        throw new UnsupportedOperationException("Соединение не интерактивно");
    }

    /** Подписаться на потоковый вывод интерактивной сессии. */
    default void onOutput(Consumer<String> listener) {
        // no-op для неинтерактивных соединений
    }

    /** Изменить размер псевдотерминала (PTY). */
    default void resize(int cols, int rows) {
        // no-op для неинтерактивных соединений
    }
}
