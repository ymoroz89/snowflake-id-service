package com.ymoroz.snowflake.snowflake.id.service;

import lombok.ToString;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ToString
@Service
public class SnowflakeService {
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long maxSequence = (1L << SEQUENCE_BITS) - 1;
    private static final long DEFAULT_CUSTOM_EPOCH = 1420070400000L;

    private final long nodeId;
    private final long customEpoch = DEFAULT_CUSTOM_EPOCH;

    private final Lock lock = new ReentrantLock();
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeService() {
        this(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "snowflake-1");
    }

    public SnowflakeService(String hostname) {
        this.nodeId = extractOrdinal(hostname);
    }

    public long nextId() {
        lock.lock();
        try {
            long currentTimestamp = timestamp();

            if (currentTimestamp < lastTimestamp) {
                throw new IllegalStateException("Invalid System Clock!");
            }

            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & maxSequence;
                if (sequence == 0) {
                    currentTimestamp = waitNextMillis(currentTimestamp);
                }
            } else {
                sequence = 0;
            }

            lastTimestamp = currentTimestamp;

            return currentTimestamp << (NODE_ID_BITS + SEQUENCE_BITS)
                    | (nodeId << SEQUENCE_BITS)
                    | sequence;
        } finally {
            lock.unlock();
        }
    }

    private long timestamp() {
        return Instant.now().toEpochMilli() - customEpoch;
    }

    private long waitNextMillis(long currentTimestamp) {
        long timestamp = currentTimestamp;
        while (timestamp == lastTimestamp) {
            timestamp = timestamp();
        }
        return timestamp;
    }

    private static long extractOrdinal(String hostname) {
        if (hostname == null) return 0;
        String[] parts = hostname.split("-");
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return 0; // fallback
        }
    }
}
