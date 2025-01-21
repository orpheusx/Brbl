package com.enoughisasgoodasafeast;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileQueueConsumer implements QueueConsumer {
    private long pollIntervalMs;
    private final BufferedReader reader;

    public FileQueueConsumer(Path queueFile) throws IOException {
        this.reader = Files.newBufferedReader(queueFile);
    }

    @Override
    public Object dequeue() throws IOException {
        return reader.readLine();
    }

    @Override
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    @Override
    public QueueConsumer setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
        return this;
    }

    public static void main(String[] args) throws IOException {
        Path source = Paths.get("src/test/resources/testMessages.txt");
        FileQueueConsumer rcvr = new FileQueueConsumer(source);
        Object message = rcvr.dequeue();
        while (message != null) {
            message = rcvr.dequeue();
            System.out.println(message);
        }
    }
}
