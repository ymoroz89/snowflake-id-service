package com.ymoroz.snowflake.snowflake.id.service;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnowflakeServiceTest {

    private static final String KUBERNETES_HOSTNAME = "snowflake-id-service-6f6dcbc498-hs2vp";

    @Test
    void testExtractOrdinalKubernetesHostname() {
        SnowflakeService service = new SnowflakeService(KUBERNETES_HOSTNAME);
        long id = service.nextId();
        long extractedNodeId = (id >> 12) & 0x3FF;
        assertEquals(385, extractedNodeId);
    }

    @Test
    void testExtractOrdinalNullHostname() {
        SnowflakeService service = new SnowflakeService(null);
        long id = service.nextId();
        long extractedNodeId = (id >> 12) & 0x3FF;
        assertEquals(0, extractedNodeId);
    }

    @Test
    void testDefaultConstructor() {
        SnowflakeService service = new SnowflakeService(KUBERNETES_HOSTNAME);
        assertNotNull(service);
        service.nextId();
    }

    @Test
    void testNextIdIncrementsSequence() {
        SnowflakeService service = new SnowflakeService(KUBERNETES_HOSTNAME);
        
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
        SnowflakeService service = new SnowflakeService(KUBERNETES_HOSTNAME);
        
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
    void testToString() {
        SnowflakeService service = new SnowflakeService(KUBERNETES_HOSTNAME);
        String toString = service.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("SnowflakeService"));
    }
}
