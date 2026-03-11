package com.ymoroz.snowflake.id.state;

import com.ymoroz.snowflake.id.config.SnowflakeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;

/**
 * Coordinates buffered, asynchronous persistence of Snowflake timestamp state.
 *
 * <p>Core behavior:
 * <ul>
 *   <li>Reserves a future timestamp (`current + buffer`) before persisting.</li>
 *   <li>Coalesces concurrent save requests by keeping only the newest reservation.</li>
 *   <li>Uses a single background worker to flush pending reservations.</li>
 *   <li>Rolls back in-memory reservation when persistence fails to allow retry.</li>
 * </ul>
 */
@Slf4j
@Component
public final class SnowflakeStatePersistenceCoordinator implements StatePersistenceCoordinator {
    private final SnowflakeStateService stateService;
    private final long timeOffsetBufferMs;
    private final ExecutorService stateSaveExecutor;
    private final AtomicBoolean stateSaveWorkerScheduled;
    private final AtomicReference<Reservation> pendingSaveReservation;
    private final AtomicLong lastSavedTimestamp;

    @Autowired
    public SnowflakeStatePersistenceCoordinator(final SnowflakeStateService stateService,
                                                final SnowflakeProperties snowflakeProperties) {
        this(stateService, snowflakeProperties.getTimeOffsetBufferMs());
    }

    SnowflakeStatePersistenceCoordinator(final SnowflakeStateService stateService,
                                         final long timeOffsetBufferMs) {
        this.stateService = Objects.requireNonNull(stateService, "stateService must not be null");
        this.timeOffsetBufferMs = timeOffsetBufferMs;
        this.stateSaveExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "snowflake-state-save");
            thread.setDaemon(true);
            return thread;
        });
        this.stateSaveWorkerScheduled = new AtomicBoolean(false);
        this.pendingSaveReservation = new AtomicReference<>();
        this.lastSavedTimestamp = new AtomicLong(-1L);
    }

    /**
     * Loads persisted timestamp and initializes in-memory reservation tracking.
     *
     * @param currentTimestamp current logical timestamp from the Snowflake service
     * @return persisted timestamp, or {@code -1} when state is absent
     */
    public long initialize(long currentTimestamp) {
        long loadedState = stateService.loadState(currentTimestamp);
        lastSavedTimestamp.set(loadedState);
        return loadedState;
    }

    /**
     * Reserves and schedules persistence for the provided timestamp if needed.
     *
     * <p>When multiple reservations race, only the newest one is kept and flushed.
     *
     * @param currentTimestamp timestamp candidate to reserve
     */
    public void persist(long currentTimestamp) {
        Reservation reservation = reserveTimestampIfNeeded(currentTimestamp);
        if (reservation == null) {
            return;
        }

        pendingSaveReservation.accumulateAndGet(reservation, selectNewerReservation());
        scheduleStateSaveWorker();
    }

    /**
     * Chooses the newer reservation between pending and incoming values.
     */
    private static BinaryOperator<Reservation> selectNewerReservation() {
        return (existing, candidate) ->
                existing == null || candidate.reservedTimestamp() > existing.reservedTimestamp()
                        ? candidate : existing;
    }

    /**
     * Stops the background worker and waits briefly for graceful completion.
     */
    public void shutdown() {
        stateSaveExecutor.shutdown();
        try {
            if (!stateSaveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("State save worker did not stop in time. Forcing shutdown.");
                stateSaveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stateSaveExecutor.shutdownNow();
        }
    }

    /**
     * Attempts to reserve {@code currentTimestamp + timeOffsetBufferMs}.
     *
     * @return reservation when timestamp advanced, otherwise {@code null}
     */
    private Reservation reserveTimestampIfNeeded(long currentTimestamp) {
        while (true) {
            long previousReservedTimestamp = lastSavedTimestamp.get();
            long reservedTimestamp = currentTimestamp + timeOffsetBufferMs;
            if (reservedTimestamp <= previousReservedTimestamp) {
                return null;
            }

            if (lastSavedTimestamp.compareAndSet(previousReservedTimestamp, reservedTimestamp)) {
                return new Reservation(previousReservedTimestamp, reservedTimestamp);
            }
        }
    }

    /**
     * Ensures exactly one worker is active to drain pending saves.
     */
    private void scheduleStateSaveWorker() {
        // nextId() and scheduled saveState() can race here; CAS allows only one drain worker.
        if (stateSaveWorkerScheduled.compareAndSet(false, true)) {
            stateSaveExecutor.execute(this::drainPendingSaves);
        }
    }

    /**
     * Drains queued reservations and persists them asynchronously.
     *
     * <p>If persistence fails, reservation rollback allows a later call to retry.
     */
    private void drainPendingSaves() {
        try {
            while (true) {
                Reservation reservation = pendingSaveReservation.getAndSet(null);
                if (reservation == null) {
                    return;
                }

                try {
                    stateService.saveState(reservation.reservedTimestamp());
                } catch (RuntimeException e) {
                    lastSavedTimestamp.compareAndSet(
                            reservation.reservedTimestamp(),
                            reservation.previousReservedTimestamp());
                    log.error("Failed to persist Snowflake state asynchronously at timestamp {}",
                            reservation.reservedTimestamp(), e);
                }
            }
        } finally {
            stateSaveWorkerScheduled.set(false);
            if (pendingSaveReservation.get() != null) {
                scheduleStateSaveWorker();
            }
        }
    }

    private record Reservation(long previousReservedTimestamp, long reservedTimestamp) {}
}
