package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.operator.ProcessState;

import java.io.*;

public record ProcessStateMessage(ProcessState processState, Message message) implements Serializable {

    public byte[] toBytes() throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new ObjectOutputStream(bos).writeObject(this);
        return bos.toByteArray();
    }

    public static ProcessStateMessage fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        return (ProcessStateMessage) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
    }

}
