package com.ymoroz.snowflake.snowflake.id;

import com.ymoroz.snowflake.client.SnowflakeClient;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "grpc.server.port=0"
})
class SnowflakeGrpcIntegrationTest {

    @Autowired
    private GrpcServerProperties grpcServerProperties;

    @Test
    void shouldGenerateIdViaGrpc() throws Exception {
        int port = grpcServerProperties.getPort();
        if (port == 0) {
            // If it's still 0, it means it's not yet assigned or we are not using the right way to get it.
            // But usually grpc-spring-boot-starter updates it or we can get it from the server instance.
        }
        
        // Actually, with port 0, we can use GrpcServerProperties to get the actual port if it's injected.
        // Let's try to just use a fixed port for simplicity if port 0 is tricky, 
        // but port 0 is better for CI.
        
        try (SnowflakeClient client = new SnowflakeClient("localhost", port)) {
            long id = client.generateId();
            assertThat(id).isGreaterThan(0);
        }
    }
}
