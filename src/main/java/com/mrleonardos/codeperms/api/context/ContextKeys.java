package com.mrleonardos.codeperms.api.context;

/**
 * Встроенные ключи контекстов.
 *
 * <p>
 * Ключи и значения пишутся в нижнем регистре. Чужой мод добавляет свои ключи с префиксом своего
 * модида, например {@code myclan.officer}, чтобы не столкнуться со встроенными.
 */
public final class ContextKeys {

    /** Имя мира, в котором игрок сейчас находится. */
    public static final String WORLD = "world";

    /** Идентификатор измерения, например {@code -1} для Незера. */
    public static final String DIM = "dim";

    /** Флаг оператора: {@code true} или {@code false}. */
    public static final String OP = "op";

    private ContextKeys() {}
}
