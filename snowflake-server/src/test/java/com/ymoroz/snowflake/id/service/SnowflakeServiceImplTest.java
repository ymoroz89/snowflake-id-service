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
    void testToString() {
        snowflakeProperties.setHostname(KUBERNETES_HOSTNAME);
        SnowflakeService service = new SnowflakeServiceImpl(mockStateService, snowflakeProperties, nodeIdParser);
        String toString = service.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("SnowflakeService"));
    }
}
