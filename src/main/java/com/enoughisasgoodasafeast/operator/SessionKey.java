package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.MessageType;

/**
 * A record that defines the key for a LoadingCache derived from a MessageType.MO.
 * Logically it's the subset of the properties contained in a Message that are needed to create a User and find the
 * initial Script graph.
 *
 * @param platform
 * @param from
 * @param to
 */
public record SessionKey(Platform platform, String from, String to) {
    /*
    * Most times the message will be an MO but a push campaign might want to start a Session with an MT
    */
    public static SessionKey newSessionKey(Message message) {
        if (MessageType.MO.equals(message.type())) {
            return new SessionKey(message.platform(), message.from(), message.to());
        } else {
            return new SessionKey(message.platform(), message.to(), message.from());
        }
    }

}

