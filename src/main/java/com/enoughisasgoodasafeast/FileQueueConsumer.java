package com.enoughisasgoodasafeast;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileQueueConsumer implements QueueConsumer {
    private Path queueFile;
    private long pollIntervalMs;
    private BufferedReader reader;

    public FileQueueConsumer(Path queueFile) throws IOException {
        this.queueFile = queueFile;
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
        Object message = null;
        while ((message = rcvr.dequeue()) != null) {
            System.out.println(message);
        }
    }
}
