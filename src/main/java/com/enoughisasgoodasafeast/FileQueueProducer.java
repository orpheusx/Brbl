package com.enoughisasgoodasafeast;

import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

public class FileQueueProducer implements QueueProducer {

    private static final Logger LOG = LoggerFactory.getLogger(FileQueueProducer.class);

    private final Path queueDirectory;

    public FileQueueProducer(Path queueDirectory) throws IOException {
        this.queueDirectory = queueDirectory;
        LOG.info("Using FileQueueProducer writing to {}", queueDirectory.toRealPath());
        if (!Files.exists(this.queueDirectory)) {
            throw new IllegalArgumentException("The queue directory, " +
                    this.queueDirectory.toString() + ", doesn't exist.");
        }
    }

    @Override
    public void enqueue(Message event) {
        Message mt = (Message)event;
        long receivedAt = NanoClock.systemUTC().nanos();
        try {
            Files.writeString(
                    Paths.get(queueDirectory.toString(),receivedAt + ".txt"),
                    mt.text(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void shutdown() throws IOException, TimeoutException {
        // no op
        LOG.info("Shutdown called.");
    }
}
