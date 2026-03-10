package com.ymoroz.snowflake.client;

import com.ymoroz.snowflake.proto.GenerateIdRequest;
import com.ymoroz.snowflake.proto.GenerateIdResponse;
import com.ymoroz.snowflake.proto.SnowflakeServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

public class SnowflakeClient implements AutoCloseable {

    private final ManagedChannel channel;
    private final SnowflakeServiceGrpc.SnowflakeServiceBlockingStub blockingStub;

    public SnowflakeClient(String host, int port) {
        this(host, port, 30, 5);
    }

    public SnowflakeClient(String host, int port, long keepAliveTime, long keepAliveTimeout) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                // Enable HTTP/2 multiplexing for connection reuse
                // Keep-alive prevents premature connection drops due to network timeouts,
                // but doesn't prevent intentional shutdown when the application is done with the connection
                .keepAliveTime(keepAliveTime, TimeUnit.SECONDS)
                .keepAliveTimeout(keepAliveTimeout, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true) // Send keep-alive pings even without active calls to prevent connection timeouts
                .build());
    }

    public SnowflakeClient(SnowflakeClientProperties properties) {
        this(properties.getHost(), properties.getPort(), properties.getKeepAliveTime(), properties.getKeepAliveTimeout());
    }

    public SnowflakeClient(ManagedChannel channel) {
        this.channel = channel;
        this.blockingStub = SnowflakeServiceGrpc.newBlockingStub(channel);
    }

    public long generateId() {
        GenerateIdRequest request = GenerateIdRequest.newBuilder().build();
        GenerateIdResponse response = blockingStub.generateId(request);
        return response.getId();
    }

    @Override
    public void close() throws Exception {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
