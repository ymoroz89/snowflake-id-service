package com.ymoroz.snowflake.id.service;

import com.ymoroz.snowflake.id.config.SnowflakeProperties;
import com.ymoroz.snowflake.id.parser.NodeIdParser;
import com.ymoroz.snowflake.id.parser.NodeIdParserImpl;
import com.ymoroz.snowflake.id.state.SnowflakeStateServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnowflakeServiceImplTest {

    private static final String KUBERNETES_HOSTNAME = "snowflake-id-service-1";
    private final SnowflakeStateServiceImpl mockStateService = mock(SnowflakeStateServiceImpl.class);
    private final SnowflakeProperties snowflakeProperties = new SnowflakeProperties();
    private final NodeIdParser nodeIdParser = new NodeIdParserImpl();


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
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);

        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            Instant fixedTime = Instant.ofEpochMilli(1700000000000L);
            mockedInstant.when(Instant::now).thenReturn(fixedTime);

            long id1 = service.nextId();
            long id2 = service.nextId();

            assertEquals(0, id1 & 0xFFF);
            assertEquals(1, id2 & 0xFFF);
            assertEquals(id1 >> 12, id2 >> 12);
        }
    }

    @Test
    void testClockDriftThrowsException() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);

        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            Instant time1 = Instant.ofEpochMilli(1700000000000L);
            Instant time2 = Instant.ofEpochMilli(1699999999999L);

            mockedInstant.when(Instant::now).thenReturn(time1);
            service.nextId();

            mockedInstant.when(Instant::now).thenReturn(time2);
            assertThrows(IllegalStateException.class, service::nextId);
        }
    }

    @Test
    void testInitLoadedState() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setCustomEpoch(1000L);
        when(mockStateService.loadState(anyLong())).thenReturn(5000L);
        
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        service.init();
        
        long id = service.nextId();
        // timestamp should be at least 5000
        assertTrue((id >> 12 + 10) >= 5000);
    }

    @Test
    void testDestroySavesState() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        service.destroy();
        verify(mockStateService, atLeastOnce()).saveState(anyLong());
    }

    @Test
    void testWaitNextMillis() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setSequenceBits(2); // Only 4 IDs per ms
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);

        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            Instant time1 = Instant.ofEpochMilli(1700000000000L);
            Instant time2 = Instant.ofEpochMilli(1700000000001L);
            
            // Initial call to set lastTimestamp
            mockedInstant.when(Instant::now).thenReturn(time1);
            service.nextId(); // seq 0, lastTimestamp = time1

            // Next 3 calls in the same millisecond
            service.nextId(); // seq 1
            service.nextId(); // seq 2
            service.nextId(); // seq 3

            // 5th call: sequence wraps to 0, currentTimestamp is still time1
            // it should call waitNextMillis
            mockedInstant.when(Instant::now).thenReturn(time1, time2);

            long id5 = service.nextId(); 
            
            assertEquals(0, id5 & 0x3);
            // timestamp in id5 should be time2
            assertEquals(time2.toEpochMilli() - snowflakeProperties.getCustomEpoch(), id5 >> 12);
        }
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
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            Instant time = Instant.ofEpochMilli(2000L);
            mockedInstant.when(Instant::now).thenReturn(time);
            service.nextId();
            verify(mockStateService, atLeastOnce()).saveState(anyLong());
        }
    }

    @Test
    void testScheduledSaveState() throws Exception {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        snowflakeProperties.setTimeOffsetBufferMs(1000L);
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        
        // Use reflection to call the private saveState method
        java.lang.reflect.Method method = SnowflakeServiceImpl.class.getDeclaredMethod("saveState");
        method.setAccessible(true);
        
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            Instant time = Instant.ofEpochMilli(5000L);
            mockedInstant.when(Instant::now).thenReturn(time);
            method.invoke(service);
            verify(mockStateService).saveState(anyLong());
        }
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
        SnowflakeServiceImpl service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        service.init();
        
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            Instant exactlyAtSaved = Instant.ofEpochMilli(1000L);
            mockedInstant.when(Instant::now).thenReturn(exactlyAtSaved);
            service.nextId();
            // currentTimestamp (1000) == lastSavedTimestamp (1000), should NOT trigger saveState
            verify(mockStateService, times(0)).saveState(anyLong());
        }
    }
}
