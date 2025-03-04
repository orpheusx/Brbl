package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.Platform;
import io.jenetics.util.NanoClock;

import java.io.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A message received by the platform.
 * We include the "to" id to support routing of multiple services
 * @param id a Brbl assigned unique identifier
 * @param received the Instant the message was received/constructed
 * @param from the senders ID (e.g. phone number for SMS)
 * @param to the ID this message was sent to (e.g. a 10DLC, shortcode, Slack channel name or the like.)
 * @param text
 */
public record MOMessage(UUID id, Instant received, Platform platform, String from, String to, String text) implements Serializable {

    public MOMessage {
        if (id == null || received == null || platform == null || from == null || to == null || text == null) { // check for empty string, too?
            throw new IllegalArgumentException("All fields are required");
        }
    }

    public  MOMessage(String from, String to, String text) {
        this(UUID.randomUUID(), NanoClock.systemUTC().instant(), Platform.BRBL, from, to, text);
    }

    public MOMessage(Platform platform, String from, String to, String text) {
        this(UUID.randomUUID(), NanoClock.systemUTC().instant(), platform, from, to, text);
    }

    // Seems like the place for these methods.
    byte[] toBytes() throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new ObjectOutputStream(bos).writeObject(this);
        return bos.toByteArray();
    }

    static MOMessage fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        return (MOMessage) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
    }

}