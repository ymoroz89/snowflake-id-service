package com.ymoroz.snowflake.id;

import com.ymoroz.snowflake.client.SnowflakeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = WebEnvironment.NONE,
    properties = {
        "spring.grpc.server.port=9090",
        "snowflake.client.port=9090",
        "snowflake.hostname=snowflake-1",
        "state.file=/tmp/snowflake.state",
        "snowflake.custom-epoch=1420070400000",
        "snowflake.node-id-bits=10",
        "snowflake.sequence-bits=12",
        "snowflake.time-offset-buffer-ms=3000"
    }
)
class SnowflakeGrpcIntegrationTest {

    @Autowired
    private SnowflakeClient snowflakeClient;

    @Test
    void shouldGenerateIdViaGrpc() throws Exception {
        long id = snowflakeClient.generateId();
        assertThat(id).isGreaterThan(0);
    }

    @Test
    void shouldGenerateMultipleIds() throws Exception {
        long id1 = snowflakeClient.generateId();
        long id2 = snowflakeClient.generateId();
        
        assertThat(id1).isGreaterThan(0);
        assertThat(id2).isGreaterThan(0);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void shouldGenerateIdsInAscendingOrder() throws Exception {
        long id1 = snowflakeClient.generateId();
        Thread.sleep(1); // Ensure time progression
        long id2 = snowflakeClient.generateId();
        
        assertThat(id1).isLessThan(id2);
    }
}
