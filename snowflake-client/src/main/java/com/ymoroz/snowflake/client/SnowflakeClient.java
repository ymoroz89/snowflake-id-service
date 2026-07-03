package com.ymoroz.snowflake.client;

import com.ymoroz.snowflake.proto.GenerateIdRequest;
import com.ymoroz.snowflake.proto.GenerateIdResponse;
import com.ymoroz.snowflake.proto.SnowflakeServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class SnowflakeClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeClient.class);

    private final ManagedChannel channel;
    private final SnowflakeServiceGrpc.SnowflakeServiceBlockingStub blockingStub;
    private final long deadline;

    public SnowflakeClient(String host, int port) {
        this(host, port, 30, 5, true, true);
    }

    public SnowflakeClient(String host, int port, long keepAliveTime, long keepAliveTimeout) {
        this(host, port, keepAliveTime, keepAliveTimeout, true, true);
    }

    public SnowflakeClient(String host, int port, long keepAliveTime, long keepAliveTimeout, boolean usePlaintext, boolean keepAliveWithoutCalls) {
        this(buildChannel(validateHost(host), validatePort(port), keepAliveTime, keepAliveTimeout, usePlaintext, keepAliveWithoutCalls), 5);
    }

    public SnowflakeClient(SnowflakeClientProperties properties) {
        this(buildChannel(
                validateHost(properties.getHost()),
                validatePort(properties.getPort()),
                properties.getKeepAliveTime(),
                properties.getKeepAliveTimeout(),
                properties.isUsePlaintext(),
                properties.isKeepAliveWithoutCalls()),
                properties.getDeadline());
    }

    public SnowflakeClient(ManagedChannel channel) {
        this(channel, 5);
    }

    public SnowflakeClient(ManagedChannel channel, long deadline) {
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.deadline = deadline;
        this.blockingStub = SnowflakeServiceGrpc.newBlockingStub(channel);
    }

    public long generateId() {
        try {
            GenerateIdRequest request = GenerateIdRequest.newBuilder().build();
            GenerateIdResponse response = blockingStub.withDeadlineAfter(deadline, TimeUnit.SECONDS).generateId(request);
            return response.getId();
        } catch (Exception e) {
            throw new SnowflakeClientException("Failed to generate ID from server", e);
        }
    }

    public boolean isConnected() {
        io.grpc.ConnectivityState state = channel.getState(false);
        return state == io.grpc.ConnectivityState.READY;
    }

    private static ManagedChannel buildChannel(String host, int port, long keepAliveTime, long keepAliveTimeout, boolean usePlaintext, boolean keepAliveWithoutCalls) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port)
                .keepAliveTime(keepAliveTime, TimeUnit.SECONDS)
                .keepAliveTimeout(keepAliveTimeout, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(keepAliveWithoutCalls);
        if (usePlaintext) {
            builder.usePlaintext();
        }
        return builder.build();
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Unable to gracefully shut down gRPC channel");
                }
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("Interrupted while shutting down gRPC channel", e);
        }
    }

    private static String validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be null or blank");
        }
        return host;
    }

    private static int validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, but was: " + port);
        }
        return port;
    }

}
