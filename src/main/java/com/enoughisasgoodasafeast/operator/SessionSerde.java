package com.enoughisasgoodasafeast.operator;

import java.io.*;

public class SessionSerde {

    public static byte[] sessionToBytes(Session session) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(session);
            return baos.toByteArray();
        }
    }

    public static Session bytesToSession(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Session) ois.readObject();
        } /*catch (IOException | ClassNotFoundException e) {
            LOG.error("Exception in bytesToSession", e);
            return null;
        }*/
    }
}
