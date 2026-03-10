package com.ymoroz.snowflake.id.service;

import com.ymoroz.snowflake.id.config.SnowflakeProperties;
import com.ymoroz.snowflake.id.parser.NodeIdParser;
import com.ymoroz.snowflake.id.parser.NodeIdParserImpl;
import com.ymoroz.snowflake.id.state.SnowflakeStateServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnowflakeServiceImplTest {

    private static final String KUBERNETES_HOSTNAME = "snowflake-id-service-1";
    private final SnowflakeStateServiceImpl mockStateService = mock(SnowflakeStateServiceImpl.class);
    private final SnowflakeProperties snowflakeProperties = new SnowflakeProperties();
    private final NodeIdParser nodeIdParser = new NodeIdParserImpl();

    private SnowflakeServiceImpl createServiceWithClock(AtomicLong currentTimeMillis) {
        return new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser, currentTimeMillis::get);
    }

    @Test
    void testExtractOrdinalKubernetesHostname() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        long id = service.nextId();
        long extractedNodeId = (id >> 12) & 0x3FF;
        assertEquals(1, extractedNodeId);
    }

    @Test
    void testExtractOrdinalNullHostname() {
        snowflakeProperties.setHostname(null);
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser));
    }

    @Test
    void testDefaultConstructor() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        assertNotNull(service);
        service.nextId();
    }

    @Test
    void testNextIdIncrementsSequence() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        AtomicLong now = new AtomicLong(1700000000000L);
        SnowflakeServiceImpl service = createServiceWithClock(now);

        long id1 = service.nextId();
        long id2 = service.nextId();

        assertEquals(0, id1 & 0xFFF);
        assertEquals(1, id2 & 0xFFF);
        assertEquals(id1 >> 12, id2 >> 12);
    }

    @Test
    void testClockDriftThrowsException() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setMaxClockBackwardWaitMs(5L);
        long firstTimestamp = 1700000000000L;
        AtomicLong now = new AtomicLong(firstTimestamp);
        SnowflakeServiceImpl service = createServiceWithClock(now);

        service.nextId();
        now.set(firstTimestamp - 20L);

        assertThrows(IllegalStateException.class, service::nextId);
    }

    @Test
    void testSmallClockDriftWaitsForRecovery() throws Exception {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setMaxClockBackwardWaitMs(10L);
        long firstTimestamp = 1700000000000L;
        AtomicLong now = new AtomicLong(firstTimestamp);
        SnowflakeServiceImpl service = createServiceWithClock(now);

        long firstId = service.nextId();
        now.set(firstTimestamp - 1L);

        Thread recoveryThread = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            now.set(firstTimestamp);
        });

        long secondId = service.nextId();
        recoveryThread.join();

        assertEquals(1, secondId & 0xFFF);
        assertEquals(firstId >> 12, secondId >> 12);
    }

    @Test
    void testInitLoadedState() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setCustomEpoch(1000L);
        when(mockStateService.loadState(anyLong())).thenReturn(5000L);

        AtomicLong now = new AtomicLong(7000L);
        SnowflakeServiceImpl service = createServiceWithClock(now);
        service.init();

        long id = service.nextId();
        long timestampPart = id >> (snowflakeProperties.getSequenceBits() + snowflakeProperties.getNodeIdBits());
        assertTrue(timestampPart >= 5000);
    }

    @Test
    void testDestroySavesState() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        AtomicLong now = new AtomicLong(1700000000000L);
        SnowflakeServiceImpl service = createServiceWithClock(now);
        service.destroy();
        verify(mockStateService, atLeastOnce()).saveState(anyLong());
    }

    @Test
    void testWaitNextMillis() throws Exception {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setSequenceBits(2); // Only 4 IDs per ms
        long baseTime = 1700000000000L;
        AtomicLong now = new AtomicLong(baseTime);
        SnowflakeServiceImpl service = createServiceWithClock(now);

        service.nextId(); // seq 0, lastTimestamp = baseTime
        service.nextId(); // seq 1
        service.nextId(); // seq 2
        service.nextId(); // seq 3

        Thread nextMillisThread = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            now.set(baseTime + 1L);
        });

        long id5 = service.nextId();
        nextMillisThread.join();

        assertEquals(0, id5 & 0x3);
        assertEquals(baseTime + 1L - snowflakeProperties.getCustomEpoch(), id5 >> 12);
    }

    @Test
    void testNodeIdOutOfRangeHigh() {
        snowflakeProperties.setHostname("snowflake-2000"); // 2000 > 1023
        snowflakeProperties.setNodeIdBits(10);
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser));
    }

    @Test
    void testNodeIdOutOfRangeLow() {
        NodeIdParser mockParser = mock(NodeIdParser.class);
        when(mockParser.parse(anyString())).thenReturn(-1L);
        snowflakeProperties.setHostname("snowflake-minus-1");
        snowflakeProperties.setNodeIdBits(10);
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeServiceImpl(mockStateService, snowflakeProperties, mockParser));
    }

    @Test
    void testValidateTimestampTriggersSave() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setCustomEpoch(0);
        snowflakeProperties.setTimeOffsetBufferMs(1000L);
        // Initially lastSavedTimestamp is -1
        AtomicLong now = new AtomicLong(2000L);
        SnowflakeServiceImpl service = createServiceWithClock(now);

        service.nextId();
        verify(mockStateService, atLeastOnce()).saveState(anyLong());
    }

    @Test
    void testScheduledSaveState() throws Exception {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setCustomEpoch(0L);
        snowflakeProperties.setTimeOffsetBufferMs(1000L);
        AtomicLong now = new AtomicLong(5000L);
        SnowflakeServiceImpl service = createServiceWithClock(now);

        // Use reflection to call the private saveState method
        java.lang.reflect.Method method = SnowflakeServiceImpl.class.getDeclaredMethod("saveState");
        method.setAccessible(true);

        method.invoke(service);
        verify(mockStateService).saveState(anyLong());
    }

    @Test
    void testToString() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        SnowflakeService service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        String toString = service.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("SnowflakeServiceImpl"));
    }

    @Test
    void testValidateTimestampExactlyAtSaved() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setCustomEpoch(0);
        when(mockStateService.loadState(anyLong())).thenReturn(1000L);
        AtomicLong now = new AtomicLong(1000L);
        SnowflakeServiceImpl service = createServiceWithClock(now);
        service.init();

        service.nextId();
        // currentTimestamp (1000) == lastSavedTimestamp (1000), should NOT trigger saveState
        verify(mockStateService, times(0)).saveState(anyLong());
    }
}
