package com.ymoroz.snowflake.id.state;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnowflakeStatePersistenceCoordinatorTest {

    private static final long TIME_OFFSET_BUFFER_MS = 10_000L;

    @Mock
    private SnowflakeStateService stateService;

    private SnowflakeStatePersistenceCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new SnowflakeStatePersistenceCoordinator(stateService, TIME_OFFSET_BUFFER_MS);
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
    }

    @Test
    void initializeLoadsStateAndReturnsPersistedTimestamp() {
        long currentTimestamp = 100L;
        long persistedTimestamp = 50L;
        when(stateService.loadState(currentTimestamp)).thenReturn(persistedTimestamp);

        long result = coordinator.initialize(currentTimestamp);

        assertThat(result).isEqualTo(persistedTimestamp);
        verify(stateService).loadState(currentTimestamp);
    }

    @Test
    void persistDoesNothingWhenTimestampIsOlderThanLastSaved() {
        when(stateService.loadState(100L)).thenReturn(100L + TIME_OFFSET_BUFFER_MS);
        coordinator.initialize(100L);

        coordinator.persist(50L);

        verify(stateService, never()).saveState(anyLong());
    }

    @Test
    void persistSchedulesSaveWhenTimestampIsNewer() {
        when(stateService.loadState(100L)).thenReturn(100L + TIME_OFFSET_BUFFER_MS);
        coordinator.initialize(100L);

        coordinator.persist(150L);

        long expectedReservedTimestamp = 150L + TIME_OFFSET_BUFFER_MS;
        verify(stateService, timeout(1000)).saveState(expectedReservedTimestamp);
    }

    @Test
    void persistCoalescesMultipleTimestampsAndSavesOnlyNewest() {
        when(stateService.loadState(100L)).thenReturn(100L + TIME_OFFSET_BUFFER_MS);
        coordinator.initialize(100L);

        coordinator.persist(120L);
        coordinator.persist(150L);
        coordinator.persist(130L);

        long expectedReservedTimestamp = 150L + TIME_OFFSET_BUFFER_MS;
        verify(stateService, timeout(1000)).saveState(expectedReservedTimestamp);
        verify(stateService, never()).saveState(120L + TIME_OFFSET_BUFFER_MS);
        verify(stateService, never()).saveState(130L + TIME_OFFSET_BUFFER_MS);
    }

    @Test
    void persistRollsBackReservationOnFailureAndAllowsRetry() throws InterruptedException {
        long initialTimestamp = 100L;
        coordinator.initialize(initialTimestamp);

        long newTimestamp = 150L;
        long reservedTimestamp = newTimestamp + TIME_OFFSET_BUFFER_MS;

        AtomicBoolean shouldThrow = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (shouldThrow.getAndSet(false)) {
                throw new RuntimeException("Persistence failed");
            }
            return null;
        }).when(stateService).saveState(reservedTimestamp);

        coordinator.persist(newTimestamp);
        verify(stateService, timeout(1000).times(1)).saveState(reservedTimestamp);

        TimeUnit.MILLISECONDS.sleep(200);

        coordinator.persist(newTimestamp);
        verify(stateService, timeout(1000).times(2)).saveState(reservedTimestamp);
    }

    @Test
    void shutdownCanBeCalledWithoutErrors() {
        assertThat(coordinator).isNotNull();
    }
}
