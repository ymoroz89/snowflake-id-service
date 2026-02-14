package com.ymoroz.snowflake.snowflake.id;

import com.ymoroz.snowflake.client.SnowflakeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.grpc.server.port=9099",
        "snowflake.client.port=9099"
})
class SnowflakeGrpcIntegrationTest {

    @Autowired
    private SnowflakeClient snowflakeClient;

    @Test
    void shouldGenerateIdViaGrpc() throws Exception {
        long id = snowflakeClient.generateId();
        assertThat(id).isGreaterThan(0);
    }
}
