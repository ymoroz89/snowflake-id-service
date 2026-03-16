package com.ymoroz.snowflake.id.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates buffered persistence of Snowflake timestamp state.
 *
 * <p>Core behavior:
 * <ul>
 *   <li>Tracks a target timestamp to persist.</li>
 *   <li>Coalesces concurrent timestamp updates by keeping only the newest target.</li>
 *   <li>Flushes pending reservations synchronously when triggered.</li>
 * </ul>
 */
@Slf4j
@Component
public final class SnowflakeStatePersistenceCoordinator implements StatePersistenceCoordinator {
    private static final long NO_PENDING_SAVE = Long.MIN_VALUE;

    private final SnowflakeStateService stateService;
    private final AtomicLong pendingSaveTimestamp;
    private final AtomicLong lastSavedTimestamp;

    @Autowired
    public SnowflakeStatePersistenceCoordinator(final SnowflakeStateService stateService) {
        this.stateService = Objects.requireNonNull(stateService, "stateService must not be null");
        this.pendingSaveTimestamp = new AtomicLong(NO_PENDING_SAVE);
        this.lastSavedTimestamp = new AtomicLong(-1L);
    }

    /**
     * Loads persisted timestamp and initializes tracking.
     *
     * @param currentTimestamp current logical timestamp from the Snowflake service
     * @return persisted timestamp, or {@code -1} when state is absent
     */
    @Override
    public long initialize(long currentTimestamp) {
        log.debug("Initializing coordinator with currentTimestamp: {}", currentTimestamp);
        long loadedState = stateService.loadState(currentTimestamp);
        lastSavedTimestamp.set(loadedState);
        log.debug("Coordinator initialized. lastSavedTimestamp: {}", loadedState);
        return loadedState;
    }

    /**
     * Updates the pending reservation timestamp if the new candidate is larger.
     *
     * @param targetTimestamp timestamp to persist
     */
    @Override
    public void persist(long targetTimestamp) {
        log.debug("Registering targetTimestamp for persistence: {}", targetTimestamp);
        pendingSaveTimestamp.accumulateAndGet(targetTimestamp, Math::max);
    }

    /**
     * Synchronously saves the pending timestamp if it is newer than the last saved one.
     */
    @Override
    public void flushPending() {
        long pending = pendingSaveTimestamp.get();
        long lastSaved = lastSavedTimestamp.get();

        if (pending > lastSaved) {
            log.debug("Flushing pending state. pending: {}, lastSaved: {}", pending, lastSaved);
            try {
                stateService.saveState(pending);
                // Only update lastSavedTimestamp if save succeeded
                lastSavedTimestamp.accumulateAndGet(pending, Math::max);
                log.debug("Successfully flushed state to: {}", pending);
            } catch (Exception e) {
                log.error("Failed to persist Snowflake state at timestamp {}", pending, e);
            }
        } else {
            log.trace("No pending state to flush. pending: {}, lastSaved: {}", pending, lastSaved);
        }
    }

    /**
     * No-op as this implementation does not use background threads.
     */
    @Override
    public void shutdown() {
        // No resources to clean up
    }
}
