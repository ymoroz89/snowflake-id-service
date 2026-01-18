package com.ymoroz.snowflake.client;

import com.ymoroz.snowflake.proto.GenerateIdRequest;
import com.ymoroz.snowflake.proto.GenerateIdResponse;
import com.ymoroz.snowflake.proto.SnowflakeServiceGrpc;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SnowflakeClientTest {

    private ManagedChannel channel;
    private SnowflakeServiceGrpc.SnowflakeServiceBlockingStub blockingStub;

    @BeforeEach
    void setUp() {
        channel = mock(ManagedChannel.class);
        blockingStub = mock(SnowflakeServiceGrpc.SnowflakeServiceBlockingStub.class);
    }

    @Test
    public void testClientInstantiationWithHostAndPort() {
        SnowflakeClient client = new SnowflakeClient("localhost", 9090);
        assertNotNull(client);
    }

    @Test
    public void testClientInstantiationWithProperties() {
        SnowflakeClientProperties properties = new SnowflakeClientProperties();
        properties.setHost("localhost");
        properties.setPort(9090);
        SnowflakeClient client = new SnowflakeClient(properties);
        assertNotNull(client);
    }

    @Test
    public void testGenerateId() {
        try (MockedStatic<SnowflakeServiceGrpc> mockedGrpc = mockStatic(SnowflakeServiceGrpc.class)) {
            mockedGrpc.when(() -> SnowflakeServiceGrpc.newBlockingStub(any(ManagedChannel.class)))
                    .thenReturn(blockingStub);

            SnowflakeClient client = new SnowflakeClient(channel);
            
            GenerateIdResponse response = GenerateIdResponse.newBuilder().setId(12345L).build();
            when(blockingStub.generateId(any(GenerateIdRequest.class))).thenReturn(response);

            long id = client.generateId();

            assertEquals(12345L, id);
            verify(blockingStub).generateId(any(GenerateIdRequest.class));
        }
    }

    @Test
    public void testClose() throws Exception {
        when(channel.shutdown()).thenReturn(channel);
        when(channel.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);

        try (MockedStatic<SnowflakeServiceGrpc> mockedGrpc = mockStatic(SnowflakeServiceGrpc.class)) {
            mockedGrpc.when(() -> SnowflakeServiceGrpc.newBlockingStub(any(ManagedChannel.class)))
                    .thenReturn(blockingStub);
            
            SnowflakeClient client = new SnowflakeClient(channel);
            client.close();

            verify(channel).shutdown();
            verify(channel).awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
