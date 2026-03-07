package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.MessageType;

import java.util.StringJoiner;

public record Event(MessageType type, String message) {

    @Override
    public String toString() {
        return new StringJoiner(", ", Event.class.getSimpleName() + "[", "]")
                .add("type=" + type)
                .add("message='" + message + "'")
                .toString();
    }
}
