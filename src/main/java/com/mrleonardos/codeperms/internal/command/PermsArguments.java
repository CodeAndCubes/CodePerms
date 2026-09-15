package com.mrleonardos.codeperms.internal.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.ArgumentTypes;
import com.mrleonardos.codecore.api.command.CommandSender;

public interface PermsArguments {

    String PERMANENT = "permanent";
    List<String> EXPIRY_SAMPLES = Collections
        .unmodifiableList(Arrays.asList("permanent", "10m", "1h", "1d", "7d", "30d"));

    ArgumentType<String> groupId();

    ArgumentType<String> trackName();

    ArgumentType<UUID> player();

    ArgumentType<String> node();

    ArgumentType<Long> expiry();

    /**
     * Срок выдачи: {@code permanent} или длительность из {@code 10m}, {@code 1h}, {@code 7d}. Разбор
     * возвращает метку истечения, ноль значит бессрочно.
     */
    static ArgumentType<Long> expiryType() {
        return new ArgumentType<Long>() {

            @Override
            public Long parse(String raw) {
                String text = raw.trim();
                if (PERMANENT.equalsIgnoreCase(text)) {
                    return 0L;
                }
                long seconds = ArgumentTypes.duration()
                    .parse(text);
                return System.currentTimeMillis() + seconds * 1000L;
            }

            @Override
            public List<String> suggestions(CommandSender sender, String partial) {
                List<String> found = new ArrayList<>();
                String prefix = partial.trim()
                    .toLowerCase(Locale.ROOT);
                for (String sample : EXPIRY_SAMPLES) {
                    if (sample.startsWith(prefix)) {
                        found.add(sample);
                    }
                }
                return found;
            }
        };
    }
}
