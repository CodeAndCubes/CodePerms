package com.mrleonardos.codeperms.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.mrleonardos.codeperms.api.context.ContextRegistry;
import com.mrleonardos.codeperms.api.manage.PermissionsEvents;
import com.mrleonardos.codeperms.api.manage.PermsAdmin;
import com.mrleonardos.codeperms.api.store.PermissionStore;

/**
 * Точка входа для чужих модов.
 *
 * <pre>
 *
 * PermsApi.contexts()
 *     .register(new ClanContexts());
 * PermsApi.registerStore(new SqlStore());
 * </pre>
 *
 * <p>
 * Провайдеров контекстов и хранилища регистрируют на инициализации своего мода: к первому использованию
 * прав всё должно быть на местах. {@link #admin()} и {@link #events()} работают после того, как реестр
 * ролей отдал права CodePerms и тот собрал свою реализацию, то есть с конца постинициализации.
 * Обращение раньше даёт понятную ошибку, а не падение.
 *
 * <p>
 * Политика реестров одна на всю линейку: имя занимается один раз, а на старте сервера регистрация
 * закрывается. Поздняя регистрация это ошибка с исключением, а не тихо потерянный провайдер.
 */
public final class PermsApi {

    private static final ContextRegistry CONTEXTS = new ContextRegistry();
    private static final NodeCatalog CATALOG = new NodeCatalog();
    private static final Map<String, PermissionStore> STORES = new TreeMap<>();

    private static volatile PermsAdmin admin;
    private static volatile PermissionsEvents events;
    private static volatile boolean frozen;

    private PermsApi() {}

    /** Правки прав. */
    public static PermsAdmin admin() {
        PermsAdmin installed = admin;
        if (installed == null) {
            throw new IllegalStateException("CodePerms is not ready yet, call it no earlier than its init phase");
        }
        return installed;
    }

    /** Реестр слушателей правок. */
    public static PermissionsEvents events() {
        PermissionsEvents installed = events;
        if (installed == null) {
            throw new IllegalStateException("CodePerms is not ready yet, call it no earlier than its init phase");
        }
        return installed;
    }

    /** Провайдеры контекстов чужих модов. */
    public static ContextRegistry contexts() {
        return CONTEXTS;
    }

    /** Каталог нод для автодополнения. */
    public static NodeCatalog catalog() {
        return CATALOG;
    }

    /**
     * Зарегистрировать хранилище. Активным становится то, чьё имя указано в ключе {@code provider}
     * секции {@code [storage]} главного файла.
     *
     * @throws IllegalArgumentException если имя уже занято другим провайдером
     * @throws IllegalStateException    если реестр уже закрыт стартом сервера
     */
    public static synchronized void registerStore(PermissionStore store) {
        Objects.requireNonNull(store, "store");
        if (frozen) {
            throw new IllegalStateException(
                "Permission stores are frozen since the server start, register " + store.id() + " in init");
        }
        PermissionStore held = STORES.get(store.id());
        if (held != null && held != store) {
            throw new IllegalArgumentException("Permission store is already registered: " + store.id());
        }
        STORES.put(store.id(), store);
    }

    /** Хранилище по имени из главного файла. */
    public static synchronized Optional<PermissionStore> store(String id) {
        return Optional.ofNullable(STORES.get(id));
    }

    /** Все зарегистрированные хранилища в алфавитном порядке имён. */
    public static synchronized List<PermissionStore> stores() {
        return new ArrayList<>(STORES.values());
    }

    /**
     * Закрыть регистрацию хранилищ и провайдеров контекстов. Зовёт сам CodePerms на старте сервера,
     * перед первой загрузкой снимка.
     */
    public static synchronized void freeze() {
        frozen = true;
        CONTEXTS.freeze();
    }

    /** Закрыта ли регистрация. */
    public static boolean frozen() {
        return frozen;
    }

    /** Подключает реализацию. Вызывается самим CodePerms: чужим модам метод не нужен. */
    public static void install(PermsAdmin installedAdmin, PermissionsEvents installedEvents) {
        admin = Objects.requireNonNull(installedAdmin, "installedAdmin");
        events = Objects.requireNonNull(installedEvents, "installedEvents");
    }

    static synchronized void reopen() {
        frozen = false;
        STORES.clear();
    }
}
