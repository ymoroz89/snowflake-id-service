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
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build());
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
