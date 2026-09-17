package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.Platform;

import java.io.*;
import java.time.Instant;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static java.time.Instant.now;

/**
 * A message received by the platform.
 * We include the "to" id to support routing of multiple services
 * @param id a Brbl assigned unique identifier
 * @param createdAt the Instant the message was constructed. Hopefully close to then time it is received/sent
 * @param type the MessageType, either MO or MT indicating the message's direction
 * @param platform the 3rd party platform
 * @param from the senders ID (e.g. phone number for SMS)
 * @param to the ID this message was sent to (e.g. a 10DLC, shortcode, Slack channel name or the like.)
 * @param text the actual message text from/to the user
 */
public record Message(UUID id, Instant createdAt, MessageType type, Platform platform, String from, String to, String text) implements Serializable {

    public Message {
        if (id == null || createdAt == null || platform == null || isInvalid(from) || isInvalid(to) || isInvalid(text)) { // check for empty string, too?
            throw new IllegalArgumentException("All fields are required");
        }
    }

    public Message(MessageType type, String from, String to, String text) {
        this(randomUUID(), now(), type, Platform.SMS, from, to, text);
    }

    public Message(MessageType type, Platform platform, String from, String to, String text) {
        this(randomUUID(), now(), type, platform, from, to, text);
    }

    public boolean isInvalid(String value) {
        return value == null || value.isBlank();
    }

    // Seems like the place for these methods.
    public byte[] toBytes() throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new ObjectOutputStream(bos).writeObject(this);
        return bos.toByteArray();
    }

    public static Message fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        return (Message) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
    }

    public static Message newMO(String from, String to, String text) {
        return new Message(MessageType.MO, from, to, text);
    }

    public static Message newMT(String from, String to, String text) {
        return new Message(MessageType.MT, from, to, text);
    }

    public static Message newMTfromMO(Message mo, String text) {
        return Message.newMT(mo.to, mo.from, text);
    }

}