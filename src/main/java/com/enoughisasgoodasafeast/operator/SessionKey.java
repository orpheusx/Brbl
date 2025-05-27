package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.MessageType;

import java.util.Objects;

/**
 * A record that defines the key for a LoadingCache derived from a MessageType.MO.
 * Logically it's the subset of the properties contained in a Message that are needed to create a User and find the
 * initial Script graph.
 * Note: We include the text of the message because (for SMS, at least) it may contain the keyword needed to find the
 * starting script. However, we don't want it to be included in the hashCode() or equals() calculation because the
 * SessionKey needs to stay consistent for session tracking purposes.
 * No doubt someone will think this code smells funny, but we are being up front about the hack.
 *
 * @param platform
 * @param from
 * @param to
 * @param keyword
 */
public record SessionKey(Platform platform, String from, String to, String keyword) {
    /*
    * Most times the message will be an MO but a push campaign might want to start a Session with an MT
    */
    public static SessionKey newSessionKey(Message message) {
        if (MessageType.MO.equals(message.type())) {
            return new SessionKey(message.platform(), message.from(), message.to(), message.text());
        } else {
            return new SessionKey(message.platform(), message.to(), message.from(), message.text());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SessionKey that = (SessionKey) o;
        return Objects.equals(to, that.to) && Objects.equals(from, that.from) && platform == that.platform;
    }

    @Override
    public int hashCode() {
        return Objects.hash(platform, from, to);
    }
}

