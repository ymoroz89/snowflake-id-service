package com.ymoroz.snowflake.id.service;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ToString
@Service
@Slf4j
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

    public SnowflakeService(@Value("${HOSTNAME}") String hostname) {
        this.nodeId = extractOrdinal(hostname);
    }

    public long nextId() {
        lock.lock();
        try {
            long currentTimestamp = timestamp();

            if (currentTimestamp < lastTimestamp) {
                log.error("Invalid System Clock!");
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
        return Optional.ofNullable(hostname)
                .filter(h -> !h.isEmpty())
                .map(h -> h.split("-"))
                .filter(parts -> parts.length > 0)
                .map(parts -> parts[parts.length - 1])
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalArgumentException("Invalid hostname: " + hostname));
    }
}
