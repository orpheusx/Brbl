package com.enoughisasgoodasafeast;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileQueueProducer implements QueueProducer {

    private Path queueDirectory;

    public FileQueueProducer(Path queueDirectory) {
        this.queueDirectory = queueDirectory;
        if (!Files.exists(this.queueDirectory)) {
            throw new IllegalArgumentException("The queue directory, " +
                    this.queueDirectory.toString() + ", doesn't exist.");
        }
    }

    @Override
    public void enqueue(Object event) {
        String eventText = (String) event;
        long receivedAt = System.currentTimeMillis();
        try {
            Files.writeString(Paths.get(
                    queueDirectory.toString(),
                    receivedAt + ".txt"),
                    eventText,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
