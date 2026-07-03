package com.ymoroz.snowflake.client;

/**
 * Exception thrown when the Snowflake gRPC client encounters an error.
 */
public class SnowflakeClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SnowflakeClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public SnowflakeClientException(String message) {
        super(message);
    }
}
