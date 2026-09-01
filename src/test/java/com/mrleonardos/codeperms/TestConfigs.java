package com.mrleonardos.codeperms;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigService;

/**
 * Настоящий ConfigService ядра поверх временной папки.
 *
 * <p>
 * Своей заглушки у CodePerms больше нет: формат файлов, комментарии и раскладка путей это поведение
 * ядра, и проверять их подделкой значит проверять подделку. Реализация лежит в dev-джаре ядра, который
 * и так стоит на тестовом classpath, поэтому она достаётся по имени класса.
 */
public final class TestConfigs {

    public static final String LINEUP = "code";
    public static final String MAIN_FILE = "config.toml";
    public static final String PERMISSIONS = "permissions";

    private static final Logger LOG = LogManager.getLogger(TestConfigs.class);

    private TestConfigs() {}

    public static ConfigService of(Path configDirectory) {
        try {
            Class<?> pathsType = Class.forName("com.mrleonardos.codecore.internal.config.ConfigPaths");
            Constructor<?> pathsConstructor = pathsType.getConstructor(Path.class);
            Object paths = pathsConstructor.newInstance(configDirectory);
            Class<?> serviceType = Class.forName("com.mrleonardos.codecore.internal.config.ConfigServiceImpl");
            Constructor<?> serviceConstructor = serviceType.getConstructor(pathsType, Logger.class);
            return (ConfigService) serviceConstructor.newInstance(paths, LOG);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                "ConfigService ядра не собрался, проверьте dev-джар CodeCore на тестовом classpath",
                failure);
        }
    }

    /** Папка линейки внутри папки конфигов игры. */
    public static Path lineup(Path configDirectory) {
        return configDirectory.resolve(LINEUP);
    }

    /** Папка прав, в которой лежат и файлы ядра, и файлы CodePerms. */
    public static Path permissions(Path configDirectory) {
        return lineup(configDirectory).resolve(PERMISSIONS);
    }

    public static Path mainFile(Path configDirectory) {
        return lineup(configDirectory).resolve(MAIN_FILE);
    }

    /** Написать главный файл до того, как ядро его прочитает. */
    public static void writeMain(Path configDirectory, String... lines) {
        write(mainFile(configDirectory), lines);
    }

    public static void write(Path file, String... lines) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Файл " + file + " не записан", failure);
        }
    }

    public static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Файл " + file + " не прочитан", failure);
        }
    }
}
