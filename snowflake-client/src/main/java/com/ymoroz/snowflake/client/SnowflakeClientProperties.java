package com.ymoroz.snowflake.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snowflake.client")
public class SnowflakeClientProperties {
    /**
     * The host of the Snowflake gRPC server.
     */
    private String host = "localhost";

    /**
     * The port of the Snowflake gRPC server.
     */
    private int port = 9090;

    /**
     * Keep-alive time in seconds. Send keep-alive ping after this duration of inactivity.
     */
    private long keepAliveTime = 30;

    /**
     * Keep-alive timeout in seconds. Wait this long for keep-alive ping response.
     */
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
