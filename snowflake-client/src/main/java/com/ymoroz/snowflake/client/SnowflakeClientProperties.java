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
}
