package com.mrleonardos.codeperms.api.resolve;

import java.util.Objects;

import com.mrleonardos.codeperms.api.model.NodeEntry;

/**
 * Сопоставление нод по сегментам с разделителем точкой.
 *
 * <p>
 * Звёздочка в конце покрывает всё, что глубже: {@code codechat.*} накрывает и
 * {@code codechat.channel.global.write}. Звёздочка в середине заменяет ровно один сегмент, поэтому
 * {@code codechat.channel.*.read} говорит про чтение любого канала, но не про запись в него. Правило
 * без звёздочек подходит только ноде с тем же числом сегментов.
 *
 * <p>
 * Точность правила равна числу сегментов без звёздочек. Из подходящих правил выигрывает самое точное, а
 * при равной точности запрет.
 *
 * <p>
 * Семантика повторяет правило ядра и держится общим файлом тестовых векторов, который гоняется и в
 * CodeCore, и здесь. Дубль осознанный: ядро не тянет за собой api мода прав.
 */
public final class NodePatterns {

    /** Сегмент, который покрывает что угодно. */
    public static final String WILDCARD = "*";

    private NodePatterns() {}

    /**
     * Подходит ли правило к ноде.
     *
     * @param pattern правило, звёздочки в нём разрешены
     * @param node    нода, про которую спрашивают; звёздочки в запросах не живут, но и не ломают проверку
     */
    public static boolean matches(String pattern, String node) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(node, "node");
        String[] patternParts = split(pattern);
        String[] nodeParts = split(node);

        for (int index = 0; index < patternParts.length; index++) {
            String part = patternParts[index];
            if (WILDCARD.equals(part)) {
                if (index == patternParts.length - 1) {
                    return true;
                }
                if (index >= nodeParts.length) {
                    return false;
                }
                continue;
            }
            if (index >= nodeParts.length || !part.equals(nodeParts[index])) {
                return false;
            }
        }
        return patternParts.length == nodeParts.length;
    }

    /** Точность правила: число сегментов без звёздочек. */
    public static int specificity(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        int score = 0;
        for (String part : split(pattern)) {
            if (!WILDCARD.equals(part)) {
                score++;
            }
        }
        return score;
    }

    private static String[] split(String value) {
        return value.split("\\" + NodeEntry.SEPARATOR, -1);
    }
}
