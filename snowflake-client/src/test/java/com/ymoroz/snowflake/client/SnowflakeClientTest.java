package com.ymoroz.snowflake.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SnowflakeClientTest {

    @Test
    public void testClientInstantiation() {
        SnowflakeClient client = new SnowflakeClient("localhost", 9090);
        assertNotNull(client);
    }
}
