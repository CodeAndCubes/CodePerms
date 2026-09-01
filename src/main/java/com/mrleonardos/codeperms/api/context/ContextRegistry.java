package com.mrleonardos.codeperms.api.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import com.mrleonardos.codeperms.api.model.ContextSet;

/**
 * Реестр провайдеров контекстов.
 *
 * <p>
 * Встроенные ключи {@code world}, {@code dim} и {@code op} платформа собирает сама, реестр добавляет
 * пары чужих модов поверх. Набор собирается один раз по событиям входа, смены измерения и респауна, а
 * не на каждой проверке права, поэтому держать его нужно в переменной, а не запрашивать заново.
 *
 * <p>
 * Регистрация закрывается на старте сервера: к первой проверке права набор провайдеров уже
 * окончательный.
 */
public final class ContextRegistry {

    private volatile List<ContextProvider> providers = Collections.emptyList();
    private volatile boolean frozen;

    /**
     * Зарегистрировать провайдера.
     *
     * @throws IllegalArgumentException если провайдер с таким именем уже зарегистрирован
     * @throws IllegalStateException    если реестр уже закрыт стартом сервера
     */
    public synchronized void register(ContextProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (frozen) {
            throw new IllegalStateException(
                "Context providers are frozen since the server start, register " + provider.id() + " in init");
        }
        for (ContextProvider registered : providers) {
            if (registered.id()
                .equals(provider.id())) {
                throw new IllegalArgumentException("Context provider is already registered: " + provider.id());
            }
        }
        List<ContextProvider> replaced = new ArrayList<>(providers);
        replaced.add(provider);
        providers = Collections.unmodifiableList(replaced);
    }

    /**
     * Снять провайдера с регистрации.
     *
     * @return правда ли провайдер нашёлся
     * @throws IllegalStateException если реестр уже закрыт стартом сервера
     */
    public synchronized boolean unregister(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        if (frozen) {
            throw new IllegalStateException(
                "Context providers are frozen since the server start, drop " + providerId + " in init");
        }
        List<ContextProvider> replaced = new ArrayList<>();
        boolean found = false;
        for (ContextProvider registered : providers) {
            if (registered.id()
                .equals(providerId)) {
                found = true;
                continue;
            }
            replaced.add(registered);
        }
        providers = Collections.unmodifiableList(replaced);
        return found;
    }

    /** Закрыть регистрацию. Зовёт сам CodePerms на старте сервера. */
    public synchronized void freeze() {
        frozen = true;
    }

    /** Закрыт ли реестр. */
    public boolean frozen() {
        return frozen;
    }

    /** Провайдеры в порядке регистрации. */
    public List<ContextProvider> providers() {
        return providers;
    }

    /**
     * Собрать пары всех провайдеров для субъекта.
     *
     * <p>
     * Провайдер, который бросил исключение, пропускается, а его имя и причина уходят в
     * {@code onFailure}: один кривой мод не отнимает контексты у остальных. Провайдер, вернувший
     * {@code null}, считается ответившим пустой картой.
     *
     * @param onFailure приёмник имени упавшего провайдера и причины, может быть {@code null}
     */
    public ContextSet collect(Object subject, BiConsumer<String, RuntimeException> onFailure) {
        if (providers.isEmpty()) {
            return ContextSet.empty();
        }
        ContextSet.Builder builder = ContextSet.builder();
        for (ContextProvider provider : providers) {
            try {
                Map<String, String> pairs = provider.collect(subject);
                if (pairs != null) {
                    builder.putAll(pairs);
                }
            } catch (RuntimeException failure) {
                if (onFailure != null) {
                    onFailure.accept(provider.id(), failure);
                }
            }
        }
        return builder.build();
    }
}
