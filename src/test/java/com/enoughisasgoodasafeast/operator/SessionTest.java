package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
//import org.apache.fory.Fory;
//import org.apache.fory.ThreadSafeFory;
//import org.apache.fory.config.CompatibleMode;
//import org.apache.fory.config.Language;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.enoughisasgoodasafeast.operator.NodeType.*;
import static com.enoughisasgoodasafeast.operator.Session.MAX_INPUT_HISTORY;
import static com.enoughisasgoodasafeast.operator.UserTest.*;
import static org.junit.jupiter.api.Assertions.*;

public class SessionTest {

    QueueProducer queueProducer = new InMemoryQueueProducer();
    TestingPersistenceManager persistenceManager = new TestingPersistenceManager();


    @Test
    void maintainInputHistorySize() {
        assertDoesNotThrow(() -> {
            String FROM = "11234567890";
            String TO = "12345";
            Session session = new Session(
                    UUID.randomUUID(),
                    new Node("do nothing", EchoWithPrefix, null),
                    new User(UUID.randomUUID(), platformIds, platformsCreated, countryCode, languages),
                    new InMemoryQueueProducer(),
                    null);
            int numElements = MAX_INPUT_HISTORY + 1;
            for (int i = 0; i < numElements; i++) {
                Message mo = Message.newMO(FROM, TO, String.valueOf(i));
                session.registerInput(mo);
            }
            session.flush(); // adds all the inputs to the inputHistory

            assertEquals(MAX_INPUT_HISTORY, session.getInputHistory().size());
            assertEquals("1", session.getInputHistory().getFirst().text());
            assertEquals("10", session.getInputHistory().getLast().text());
        });
    }

    @Test
    void serializeSession() {

        final int numSessions = 100;
        final int numMessages = 20;
        final String fileName = "serializeSessionTest.ser";
        File file = new File(fileName);
        System.out.println("Test file = " + file.getAbsolutePath());

        assertDoesNotThrow(() -> {
            FileOutputStream fos
                    = new FileOutputStream(file);
            ObjectOutputStream oos
                    = new ObjectOutputStream(fos);
            List<Session> sessions = new ArrayList<>(numSessions);
            for (int i = 0; i < numSessions; i++) {
                String FROM = "11234567890";
                String TO = "12345";
                Session session = newSession()/*new Session(
                        UUID.randomUUID(),
                        new Node("The quick brown fox jumps over the lazy dog.", NodeType.SendMessage, "A test Node"), // TODO attach Edges
                        new User(UUID.randomUUID(), platformIds, platformsCreated, countryCode, languages),
                        new InMemoryQueueProducer(),
                        null)*/;
                for (int j = 0; j < numMessages; j++) {
                    Message mo = Message.newMO(FROM, TO, String.valueOf(j));
                    session.registerInput(mo);
                }

                sessions.add(session);
            }

            long start = System.currentTimeMillis();
            oos.writeObject(sessions);
            oos.flush();
            oos.close();
            System.out.println("Serialization time = " + (System.currentTimeMillis() - start));

//            File file = new File(fis); // Replace with your file path
//            if (file.exists() && file.isFile()) {
//                long fileSize = file.length();
//                System.out.println("File size (using File.length()): " + fileSize + " bytes");
//            } else {
//                System.out.println("File does not exist or is a directory.");
//            }

            FileInputStream fis
                    = new FileInputStream(file);
            ObjectInputStream ois
                    = new ObjectInputStream(fis);

            start = System.currentTimeMillis();
            @SuppressWarnings("unchecked")
            List<Session> deserializedSessions = (List<Session>) ois.readObject();
            System.out.println("Deserialization time = " + (System.currentTimeMillis() - start));
            // Anecdotally on M1 the times are around 10 and 11 respectively.

            fis.close();

            final long size = Files.size(file.toPath());
            System.out.println("Size of file in bytes: " + size);
        });

    }



//    @Test
//    void serializeSessionUsingFury() {
//
//        final int numSessions = 100;
//        final int numMessages = 20;
//        final String fileName = "serializeSessionTest.flr";
//        File file = new File(fileName);
//        System.out.println("Test file = " + file.getAbsolutePath());
//
//        assertDoesNotThrow(() -> {
//            List<Session> sessions = new ArrayList<>(numSessions);
//            for (int i = 0; i < numSessions; i++) {
//                String FROM = "11234567890";
//                String TO = "12345";
//                Session session = newSession()/*new Session(
//                        UUID.randomUUID(),
//                        new Node("The quick brown fox jumps over the lazy dog.", NodeType.SendMessage, "A test Node"), // TODO attach Edges
//                        new User(UUID.randomUUID(), platformIds, platformsCreated, countryCode, languages),
//                        new InMemoryQueueProducer(),
//                        null)*/;
//                for (int j = 0; j < numMessages; j++) {
//                    Message mo = Message.newMO(FROM, TO, String.valueOf(j));
//                    session.registerInput(mo);
//                }
//
//                sessions.add(session);
//            }
//
//            ThreadSafeFory fory = Fory.builder()
//                    .withLanguage(Language.JAVA)
//                    // Enable reference tracking for shared/circular reference.
//                    // Disable it will have better performance if no duplicate reference.
//                    .withRefTracking(true)
//                    // compress int for smaller size
//                    // .withIntCompressed(true)
//                    // compress long for smaller size
//                    // .withLongCompressed(true)
////                    .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
//                    // enable type forward/backward compatibility
//                    // disable it for small size and better performance.
//                    // .withCompatibleMode(CompatibleMode.COMPATIBLE)
//                    // enable async multithreaded compilation.
//                    .withAsyncCompilation(true)
//                    .buildThreadSafeFory();
//
//            fory.register(Node.class);
//            fory.register(NodeType.class);
//            fory.register(Platform.class);
//            fory.register(User.class);
//            fory.register(MessageType.class);
//            fory.register(Message.class);
//            fory.register(Session.class);
//            fory.register("com.enoughisasgoodasafeast.operator.Session$1");
//
//            FileOutputStream fos
//                    = new FileOutputStream(file);
//
//            long start = System.currentTimeMillis();
//            final byte[] forySerialized = fory.serialize(sessions);
//            System.out.println("Fory serialization time = " + (System.currentTimeMillis() - start));
//            fos.write(forySerialized);
//            fos.close();
//            System.out.println("Fory serialize time = " + (System.currentTimeMillis() - start));
//            System.out.println("Fory serialized size = " + forySerialized.length);
//
//            // Now read it all back in from the file and deserialize back into objects.
////            FileInputStream fis
////                    = new FileInputStream(file);
////            ObjectInputStream ois
////                    = new ObjectInputStream(fis);
////
////            start = System.currentTimeMillis();
////            @SuppressWarnings("unchecked")
////            List<Session> deserializedSessions = (List<Session>) ois.readObject();
////            System.out.println("Deserialization time = " + (System.currentTimeMillis() - start));
////            // Anecdotally on M1 the times are around 10 and 11 respectively.
////
////            fis.close();
////
////            final long size = Files.size(file.toPath());
////            System.out.println("Size of file in bytes: " + size);
//        });
//
//    }

    private Session newSession() {
        return newSession(UUID.randomUUID());
    }

    private Session newSession(UUID id) {
        return new Session(
                id,
                new Node("The quick brown fox jumps over the lazy dog.", NodeType.SendMessage, "A test Node"),
                new User(UUID.randomUUID(), platformIds, platformsCreated, countryCode, languages),
                queueProducer,
                persistenceManager);
    }

    @Test
    void serializeSessionAsByteStream() {

        int numSessions = 500;
        int numMessages = 20;

        assertDoesNotThrow(() -> {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);

            List<Session> sessions = new ArrayList<>(numSessions);
            for (int i = 0; i < numSessions; i++) {
                String FROM = "11234567890";
                String   TO = "12345";
                Session session = newSession();
                for (int j = 0; j < numMessages; j++) {
                    Message mo = Message.newMO(FROM, TO, String.valueOf(j));
                    session.registerInput(mo);
                }

                sessions.add(session);
            }

            long start = System.currentTimeMillis();
            oos.writeObject(sessions);
            final byte[] byteArray = baos.toByteArray();

            oos.flush();
            oos.close();
            System.out.println("Serialization time = " + (System.currentTimeMillis() - start));

            System.out.println("Num bytes: " + byteArray.length);

//            start = System.currentTimeMillis();
//            @SuppressWarnings("unchecked")
//            List<Session> deserializedSessions = (List<Session>) ois.readObject();
//            System.out.println("Deserialization time = " + (System.currentTimeMillis() - start));
        });

    }

    @Test
    public void serializeToPostgres() {
        assertDoesNotThrow(() -> {
            PostgresPersistenceManager ppm = (PostgresPersistenceManager) PostgresPersistenceManager.createPersistenceManager(
                    ConfigLoader.readConfig("persistence_manager_test.properties"));

            final UUID sessionId = UUID.fromString("8d8e3123-b208-497c-b51f-18d517362433");

            // FIXME add the new method to the interface so the cast isn't needed.
            final Session session = newSession(sessionId);

            if (!ppm.upsertSession(session)) {
                throw new RuntimeException("upsertSession failed");
            }

            // Now fetch it back and check values
            final Session clone = ppm.getSession(sessionId);
            /* FIXME
               Need to be able set the queueProducer and persistenceManager unless we prefer to move those out of Session
               class and use independent functions.
            */
            System.out.println(session);
            System.out.println(clone);

            assertEquals(session.getId().toString(), clone.getId().toString());
            assertEquals(session.currentNode, clone.currentNode);

            System.out.println("Cool.");

        });
    }
}