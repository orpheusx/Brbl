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
 * @param received the Instant the message was constructed. Hopefully close to then time it is received/sent
 * @param type the MessageType, either MO or MT indicating the message's direction
 * @param platform the 3rd party platform
 * @param from the senders ID (e.g. phone number for SMS)
 * @param to the ID this message was sent to (e.g. a 10DLC, shortcode, Slack channel name or the like.)
 * @param text the actual message text from/to the user
 */
public record Message(UUID id, Instant received, MessageType type, Platform platform, String from, String to, String text) implements Serializable {

    public Message {
        if (id == null || received == null || platform == null || from == null || to == null || text == null) { // check for empty string, too?
            throw new IllegalArgumentException("All fields are required");
        }
    }

    public Message(MessageType type, String from, String to, String text) {
        this(UUID.randomUUID(), NanoClock.utcInstant(), type, Platform.BRBL, from, to, text);
    }

    public Message(MessageType type, Platform platform, String from, String to, String text) {
        this(UUID.randomUUID(), NanoClock.utcInstant(), type, platform, from, to, text);
    }

    // Seems like the place for these methods.
    byte[] toBytes() throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new ObjectOutputStream(bos).writeObject(this);
        return bos.toByteArray();
    }

    static Message fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        return (Message) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
    }

    public static Message newMO(String from, String to, String text) {
        return new Message(MessageType.MO, from, to, text);
    }

    public static Message newMT(String from, String to, String text) {
        return new Message(MessageType.MT, from, to, text);
    }

    public static Message newMTfromMO(Message mo, String text) {
        return Message.newMO(mo.to, mo.from, text);
    }

}