package com.ymoroz.snowflake.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowflakeClientPropertiesTest {

    @Test
    void testGettersAndSetters() {
        SnowflakeClientProperties properties = new SnowflakeClientProperties();
        
        properties.setHost("test-host");
        assertEquals("test-host", properties.getHost());
        
        properties.setPort(1234);
        assertEquals(1234, properties.getPort());
    }

    @Test
    void testDefaultValues() {
        SnowflakeClientProperties properties = new SnowflakeClientProperties();
        assertEquals("localhost", properties.getHost());
        assertEquals(9090, properties.getPort());
    }
}
