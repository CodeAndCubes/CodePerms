package com.mrleonardos.codeperms.api.store;

import java.util.Objects;
import java.util.Optional;

/**
 * Итог правки или записи: сделано либо отказ с причиной из конечного перечня.
 *
 * <p>
 * Отказ приходит значением, а не исключением, чтобы команда показала его игроку, а чужой мод честно
 * разобрал, что именно не вышло.
 */
public final class OperationResult {

    /** Почему правка не прошла. */
    public enum Failure {

        /** Субъекта нет: группа, игрок или трек. */
        NOT_FOUND,

        /** Субъект с таким идентификатором уже есть. */
        ALREADY_EXISTS,

        /** Потолок из {@code PermsLimits} исчерпан. */
        LIMIT_REACHED,

        /** Значение не подходит: нода, идентификатор, ключ или значение мета. */
        INVALID_VALUE,

        /** Родитель замыкает наследование в цикл. */
        INHERITANCE_CYCLE,

        /**
         * На субъекта смотрит настройка главного файла, поэтому правка в снимке разъехалась бы с ним.
         * Сначала правится конфиг.
         */
        IN_USE,

        /** Хранилище не умеет такую операцию. */
        UNSUPPORTED,

        /**
         * Правка не дошла до главного потока за отведённое время и снята с очереди: хранилище не тронуто,
         * повтор безопасен.
         */
        TIMEOUT,

        /** Хранилище не смогло применить правку, модель остаётся на последнем снимке. */
        PROVIDER_FAILED
    }

    private static final OperationResult SUCCESS = new OperationResult(null, null);

    private final Failure failure;
    private final String message;

    private OperationResult(Failure failure, String message) {
        this.failure = failure;
        this.message = message;
    }

    /** Правка прошла. */
    public static OperationResult success() {
        return SUCCESS;
    }

    /**
     * Правка прошла.
     *
     * @param message пояснение для человека или журнала
     */
    public static OperationResult success(String message) {
        Objects.requireNonNull(message, "message");
        return new OperationResult(null, message);
    }

    /**
     * Правка не прошла.
     *
     * @param message пояснение для человека или журнала
     */
    public static OperationResult failure(Failure failure, String message) {
        Objects.requireNonNull(failure, "failure");
        return new OperationResult(failure, message);
    }

    /** Правда ли правка прошла. */
    public boolean successful() {
        return failure == null;
    }

    /** Причина отказа. */
    public Optional<Failure> failure() {
        return Optional.ofNullable(failure);
    }

    /** Пояснение отказа или пустой ответ. */
    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OperationResult)) {
            return false;
        }
        OperationResult that = (OperationResult) other;
        return failure == that.failure && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(failure) * 31 + Objects.hashCode(message);
    }

    @Override
    public String toString() {
        return successful() ? "success" : failure + ": " + message;
    }
}
