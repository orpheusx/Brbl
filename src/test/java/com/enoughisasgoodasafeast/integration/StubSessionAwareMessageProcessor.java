package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.ProcessStateSession;
import com.enoughisasgoodasafeast.SessionAwareMessageProcessor;
import com.enoughisasgoodasafeast.operator.ProcessState;
import com.enoughisasgoodasafeast.operator.Session;
import com.enoughisasgoodasafeast.operator.UserStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A configurable stub implementation of {@link SessionAwareMessageProcessor} for use in integration tests.
 *
 * <p>Allows callers to control:
 * <ul>
 *   <li>The {@link ProcessState} returned by {@link #process(Message)}</li>
 *   <li>Whether {@link #complete} throws an exception (to simulate a DB commit failure)</li>
 * </ul>
 *
 * <p>All method calls are counted so tests can verify interaction counts.
 */
public class StubSessionAwareMessageProcessor implements SessionAwareMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(StubSessionAwareMessageProcessor.class);

    private volatile ProcessState processState = ProcessState.OK;
    private volatile boolean completeThrows = false;

    private final AtomicInteger processCallCount = new AtomicInteger(0);
    private final AtomicInteger completeCallCount = new AtomicInteger(0);
    private final AtomicInteger logCallCount = new AtomicInteger(0);

    /**
     * Sets the {@link ProcessState} that will be returned by the next call(s) to {@link #process(Message)}.
     *
     * @param state the desired process state
     * @return this stub, for chaining
     */
    public StubSessionAwareMessageProcessor setReturnedProcessState(ProcessState state) {
        this.processState = state;
        return this;
    }

    /**
     * If {@code true}, the next call to {@link #complete} will throw a {@link RuntimeException},
     * simulating a database commit failure.
     *
     * @param shouldThrow whether {@code complete} should throw
     * @return this stub, for chaining
     */
    public StubSessionAwareMessageProcessor setThrowsOnComplete(boolean shouldThrow) {
        this.completeThrows = shouldThrow;
        return this;
    }

    public int getProcessCallCount() {
        return processCallCount.get();
    }

    public int getCompleteCallCount() {
        return completeCallCount.get();
    }

    public int getLogCallCount() {
        return logCallCount.get();
    }

    // ---- SessionAwareMessageProcessor implementation ----

    @Override
    public ProcessStateSession process(Message message) {
        processCallCount.incrementAndGet();
        LOG.info("StubSessionAwareMessageProcessor.process() called, returning {}", processState);
        return new ProcessStateSession(processState, null);
    }

    @Override
    public boolean log(Session session, Message message) {
        logCallCount.incrementAndGet();
        LOG.info("StubSessionAwareMessageProcessor.log() called");
        return true;
    }

    @Override
    public void complete(Message message, Session session) {
        completeCallCount.incrementAndGet();
        LOG.info("StubSessionAwareMessageProcessor.complete() called, completeThrows={}", completeThrows);
        if (completeThrows) {
            throw new RuntimeException("Simulated commit failure in StubSessionAwareMessageProcessor.complete()");
        }
    }

    @Override
    public void complete(Message message, Session session, boolean isNewUser, @Nullable UserStatus updatedUserStatus) {
        // Delegate to the primary implementation so completeCallCount is always incremented.
        complete(message, session);
    }
}
