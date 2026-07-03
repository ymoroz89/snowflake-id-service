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
     * Keep-alive time in seconds. Send keep-alive ping after this duration of inactivity.
     */
    @Positive
    private long keepAliveTime = 30;

    /**
     * Keep-alive timeout in seconds. Wait this long for keep-alive ping response.
     */
    @Positive
    private long keepAliveTimeout = 5;

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
}
