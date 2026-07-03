package com.ymoroz.snowflake.client;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "snowflake.client")
@Validated
public class SnowflakeClientProperties {
    /**
     * Whether the Snowflake gRPC client is enabled.
     */
    private boolean enabled = true;

    /**
     * The host of the Snowflake gRPC server.
     */
    @NotBlank
    private String host = "localhost";

    /**
     * The port of the Snowflake gRPC server.
     */
    @Min(1)
    @Max(65535)
    private int port = 9090;

    /**
     * Whether to use plaintext (no TLS) for the gRPC connection.
     */
    private boolean usePlaintext = true;

    /**
     * Deadline in seconds for gRPC calls. Maximum time to wait for a response.
     */
    @Positive
    private long deadline = 5;

    /**
     * Keep-alive time in seconds. Send keep-alive ping after this duration of inactivity.
     */
    @Positive
    private long keepAliveTime = 30;

    /**
     * Keep-alive timeout in seconds. Wait this long for keep-alive ping response.
     */
    @Positive
    private long keepAliveTimeout = 5;

    /**
     * Whether to send keep-alive pings even when there are no active RPCs.
     */
    private boolean keepAliveWithoutCalls = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isUsePlaintext() {
        return usePlaintext;
    }

    public void setUsePlaintext(boolean usePlaintext) {
        this.usePlaintext = usePlaintext;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public long getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    public void setKeepAliveTimeout(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public boolean isKeepAliveWithoutCalls() {
        return keepAliveWithoutCalls;
    }

    public void setKeepAliveWithoutCalls(boolean keepAliveWithoutCalls) {
        this.keepAliveWithoutCalls = keepAliveWithoutCalls;
    }
}
