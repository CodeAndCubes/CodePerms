package com.mrleonardos.codeperms.api.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class NodePatternsTest {

    private static final String VECTORS = "/fixtures/permission-vectors.md";

    @TestFactory
    java.util.stream.Stream<DynamicTest> vectorHolds() {
        return DynamicTest.stream(readVectors().iterator(), Vector::describe, vector -> {
            assertEquals(vector.matches, NodePatterns.matches(vector.pattern, vector.node), vector.describe());
            assertEquals(vector.specificity, NodePatterns.specificity(vector.pattern), vector.describe());
        });
    }

    @Test
    void vectorFileIsLoadedFully() {
        List<Vector> vectors = readVectors();
        assertTrue(vectors.size() >= 20, "в файле векторов должно быть хотя бы 20 строк, а найдено " + vectors.size());
    }

    @Test
    void nullArgumentsAreRefused() {
        assertThrows(NullPointerException.class, () -> NodePatterns.matches(null, "codechat"));
        assertThrows(NullPointerException.class, () -> NodePatterns.matches("codechat", null));
        assertThrows(NullPointerException.class, () -> NodePatterns.specificity(null));
    }

    private static List<Vector> readVectors() {
        InputStream stream = NodePatternsTest.class.getResourceAsStream(VECTORS);
        if (stream == null) {
            throw new IllegalStateException("Vector file is missing: " + VECTORS);
        }
        List<Vector> vectors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Vector vector = Vector.parse(line);
                if (vector != null) {
                    vectors.add(vector);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Vector file cannot be read: " + VECTORS, failure);
        }
        return vectors;
    }

    /** Одна строка файла векторов. */
    static final class Vector {

        final String pattern;
        final String node;
        final boolean matches;
        final int specificity;

        private Vector(String pattern, String node, boolean matches, int specificity) {
            this.pattern = pattern;
            this.node = node;
            this.matches = matches;
            this.specificity = specificity;
        }

        static Vector parse(String line) {
            if (line == null || !line.startsWith("|")) {
                return null;
            }
            String[] cells = line.split("\\|", -1);
            if (cells.length < 6) {
                return null;
            }
            String expected = cells[3].trim();
            if (!expected.equals("true") && !expected.equals("false")) {
                return null;
            }
            return new Vector(
                cells[1].trim(),
                cells[2].trim(),
                Boolean.parseBoolean(expected),
                Integer.parseInt(cells[4].trim()));
        }

        String describe() {
            return pattern + " vs " + node + ": " + matches;
        }
    }
}
