package com.ymoroz.snowflake.snowflake.id.service;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnowflakeServiceTest {

    @Test
    void testExtractOrdinal() {
        SnowflakeService service = new SnowflakeService("snowflake-5");
        // We can't easily access nodeId because it's private and no getter, 
        // but we can infer it from the generated ID if we know the structure.
        // Or we use reflection for verification if absolutely necessary, 
        // but let's try to verify via generated IDs first.
        long id = service.nextId();
        long extractedNodeId = (id >> 12) & 0x3FF;
        assertEquals(5, extractedNodeId);

        assertEquals(10, (new SnowflakeService("snowflake-10").nextId() >> 12) & 0x3FF);
        assertEquals(0, (new SnowflakeService("invalid").nextId() >> 12) & 0x3FF);
        assertEquals(0, (new SnowflakeService("no-number-here").nextId() >> 12) & 0x3FF);
        assertEquals(0, (new SnowflakeService(null).nextId() >> 12) & 0x3FF);
    }

    @Test
    void testDefaultConstructor() {
        SnowflakeService service = new SnowflakeService();
        assertNotNull(service);
        // Should not throw exception
        service.nextId();
    }

    @Test
    void testNextIdIncrementsSequence() {
        SnowflakeService service = new SnowflakeService("snowflake-1");
        
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
        SnowflakeService service = new SnowflakeService("snowflake-1");
        
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            Instant time1 = Instant.ofEpochMilli(1700000000000L);
            Instant time2 = Instant.ofEpochMilli(1699999999999L); // back in time
            
            mockedInstant.when(Instant::now).thenReturn(time1);
            service.nextId();
            
            mockedInstant.when(Instant::now).thenReturn(time2);
            assertThrows(IllegalStateException.class, service::nextId);
        }
    }

    @Test
    void testSequenceWrapAroundWaitNextMillis() {
        SnowflakeService service = new SnowflakeService("snowflake-1");
        
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            Instant time1 = Instant.ofEpochMilli(1700000000000L);
            Instant time2 = Instant.ofEpochMilli(1700000000001L);
            
            // We need to exhaust the sequence (4096 IDs) in the same millisecond
            mockedInstant.when(Instant::now).thenReturn(time1);
            
            for (int i = 0; i < 4096; i++) {
                service.nextId();
            }
            
            // The next call should trigger waitNextMillis. 
            // We set the next mock return to time2 so it breaks out of the while loop.
            mockedInstant.when(Instant::now).thenReturn(time1, time2);
            
            long id = service.nextId();
            
            // Verify it's on the next millisecond and sequence is reset to 0
            long customEpoch = 1420070400000L;
            long expectedTimestamp = 1700000000001L - customEpoch;
            assertEquals(expectedTimestamp, id >> 22);
            assertEquals(0, id & 0xFFF);
        }
    }
    
    @Test
    void testToString() {
        SnowflakeService service = new SnowflakeService("snowflake-1");
        String toString = service.toString();
        assertTrue(toString.contains("nodeId=1"));
    }
}
