package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.Message;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UserActor {

    private static final Logger LOG = LoggerFactory.getLogger(UserActor.class);

    private final String phoneNumber;
    private final List<ChttrScript> scripts;

    private final int numScripts;
    private int scriptIndex, numEvents, eventIndex;

    final List<Message> rcvdMessages;
    final List<Message> sentMessages;

    final List<String> unexpectedMessages;
    final List<String> sendMessageFailures;

    public UserActor(String phoneNumber, List<ChttrScript> scripts) {
        this.phoneNumber = phoneNumber;
        this.scripts = scripts;
        this.numScripts = scripts.size();
        this.numEvents = scripts.getFirst().getEvents().size();
        this.rcvdMessages = new ArrayList<>();
        this.sentMessages = new ArrayList<>();
        this.unexpectedMessages = new ArrayList<>();
        this.sendMessageFailures = new ArrayList<>();
        LOG.info("UserActor created: phone num: {} with {} scripts: {}", phoneNumber, scripts.size(), scripts);
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    // Oof, this and the next method are stinky. We need a cleaner way to keep track of our position in the immutable list of scripts
    public synchronized Event currentEvent() {
        if(scriptIndex >= numScripts) {
            LOG.info("No more scripts available!");
            return null;
        }

        LOG.info("User {} at event/script index: {}/{}", phoneNumber, eventIndex, scriptIndex); // FIXME -> debug only

        final ChttrScript script = scripts.get(scriptIndex);
        return script.getEvents().get(eventIndex);
    }

    public synchronized @Nullable Event nextEvent() {
        if (1 + eventIndex >= numEvents) {
            eventIndex = 0;
            scriptIndex += 1;
            if (scriptIndex >= numScripts) {
                LOG.warn("Reached end of scripts.");
                return null;
            }
            numEvents = scripts.get(scriptIndex).getEvents().size();
            LOG.info("Advanced to script index: {}", scriptIndex);

        } else {
            ++eventIndex;
        }

        return currentEvent();
    }

    public synchronized void recordUnexpectedMessage(String message) {
        unexpectedMessages.add(String.join("|", String.valueOf(scriptIndex), String.valueOf(eventIndex), message));
    }

    public synchronized void recordSendMessageFail(String message) {
        sendMessageFailures.add(String.join("|", String.valueOf(scriptIndex), String.valueOf(eventIndex), message));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UserActor.class.getSimpleName() + "[", "]")
                .add("phoneNumber='" + phoneNumber + "'")
                .add("scripts=" + scripts)
                .add("rcvdMessages=" + rcvdMessages)
                .add("sentMessages=" + sentMessages)
                .toString();
    }
}
