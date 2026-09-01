package com.mrleonardos.codeperms.internal.command;

import java.util.UUID;

import com.mrleonardos.codecore.api.command.ArgumentType;

public interface PermsArguments {

    ArgumentType<String> groupId();

    ArgumentType<String> trackName();

    ArgumentType<UUID> player();

    ArgumentType<String> node();
}
