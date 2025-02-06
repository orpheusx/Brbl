package com.enoughisasgoodasafeast;

import io.jenetics.util.NanoClock;

import java.time.Instant;
import java.util.UUID;

/**
 * We include the "from" id to support routing of multiple services
 * @param id a Brbl assigned unique identifier
 * @param created the Instant the message was constructed, not when it was delivered
 * @param from the senders ID (e.g. phone number for SMS)
 * @param to the ID this message was sent to (e.g. a 10DLC or shortcode)
 * @param text
 */
public record MTMessage(UUID id, Instant created, String from, String to, String text) {

    public MTMessage {
        if (id == null || created == null || from == null || to == null || text == null) {
            // check for empty string, too?
            throw new IllegalArgumentException("All fields are required");
        }
    }

    public MTMessage(String from, String to, String text) {
        this(UUID.randomUUID(), NanoClock.systemUTC().instant(), from, to, text);
    }
}
