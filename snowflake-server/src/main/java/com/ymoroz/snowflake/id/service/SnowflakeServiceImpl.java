package com.ymoroz.snowflake.id.service;

import com.ymoroz.snowflake.id.config.SnowflakeProperties;
import com.ymoroz.snowflake.id.state.SnowflakeStateService;
import com.ymoroz.snowflake.id.parser.NodeIdParser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Distributed ID generation service based on Twitter's Snowflake algorithm.
 * 
 * <p>Generates unique, time-ordered 64-bit IDs suitable for distributed systems.
 * The ID structure consists of:
 * <ul>
 *   <li>41 bits: Timestamp (milliseconds since custom epoch 2015-01-01)</li>
 *   <li>10 bits: Node identifier (supports up to 1024 nodes)</li>
 *   <li>12 bits: Sequence number (supports 4096 IDs per millisecond per node)</li>
 * </ul>
 * 
 * @author Yurii Moroz
 * @since 1.0.0
 */
@ToString
@Service
@Slf4j
public class SnowflakeServiceImpl implements SnowflakeService {
    private final SnowflakeStateService stateService;
    private final NodeIdParser nodeIdParser;
    private final SnowflakeProperties snowflakeProperties;
    private final long nodeId;
    private final long maxSequence;
    private final int sequenceBits;
    private final int timestampShiftBits;
    private final long timeOffsetBufferMs;
    private final ReentrantLock lock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;
    private final AtomicLong lastSavedTimestamp = new AtomicLong(-1L);

    public SnowflakeServiceImpl(final SnowflakeStateService stateService,
                                final SnowflakeProperties snowflakeProperties,
                                final NodeIdParser nodeIdParser) {
        this.sequenceBits = snowflakeProperties.getSequenceBits();
        this.nodeIdParser = nodeIdParser;
        this.maxSequence = (1L << sequenceBits) - 1;
        this.timestampShiftBits = snowflakeProperties.getNodeIdBits() + sequenceBits;
        this.timeOffsetBufferMs = snowflakeProperties.getTimeOffsetBufferMs();
        this.nodeId = parseNodeId(snowflakeProperties.getHostname(), snowflakeProperties.getNodeIdBits());
        this.stateService = stateService;
        this.snowflakeProperties = snowflakeProperties;
        this.lock = new ReentrantLock();
    }

    private long parseNodeId(String hostName, int nodeIdBits) {
        long maxNodeId = (1L << nodeIdBits) - 1;
        long parsedNodeId = nodeIdParser.parse(hostName);
        if (parsedNodeId < 0 || parsedNodeId > maxNodeId) {
            throw new IllegalArgumentException("Node ID out of range for configured nodeIdBits: " + parsedNodeId);
        }
        return parsedNodeId;
    }

    @PostConstruct
    public void init() {
        long currentTime = System.currentTimeMillis() - snowflakeProperties.getCustomEpoch();
        long loadedState = stateService.loadState(currentTime);
        lastSavedTimestamp.set(loadedState);
        lastTimestamp = Math.max(lastTimestamp, loadedState);
    }

    @PreDestroy
    public void destroy() {
        saveState(); // Save one last time on shutdown
    }

    @Override
    public long nextId() {
        lock.lock();
        try {
            long currentTimestamp = currentTimestamp();
            validateTimestamp(currentTimestamp);
            validateSystemClock(currentTimestamp);

            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & maxSequence;

                //When the sequence wraps around to 0 (meaning we've generated 4096 IDs in the same millisecond)
                //    1000000000000  (4096)
                //  & 0111111111111  (4095, padded to 13 bits)
                //  = 0000000000000  (0)
                if (sequence == 0) {
                    currentTimestamp = waitNextMillis(currentTimestamp);
                }
            } else {
                sequence = 0;
            }

            lastTimestamp = currentTimestamp;
            return buildId(currentTimestamp);
        } finally {
            lock.unlock();
        }
    }

    private void validateTimestamp(long currentTimestamp) {
        // Ensure we don't generate IDs ahead of our reserved time window
        // This is a safety check, though loadState handles the initial catch-up
        if (currentTimestamp > lastSavedTimestamp.get()) {
            // If we drifted past our reservation (e.g. scheduler stalled), save immediately
            // We still need to lock here or just call the non-locking saveState
            // But since this is a critical path fallback, calling saveState() is fine.
            // However, saveState() is now non-locking, so it's safe.
            saveState();
        }
    }

    private void validateSystemClock(long currentTimestamp) {
        if (currentTimestamp < lastTimestamp) {
            log.error("Invalid System Clock! Current: {}, Last: {}", currentTimestamp, lastTimestamp);
            throw new IllegalStateException("Invalid System Clock!");
        }
    }

    private long currentTimestamp() {
        return Instant.now().toEpochMilli() - snowflakeProperties.getCustomEpoch();
    }

    private long buildId(long currentTimestamp) {
        return (currentTimestamp << timestampShiftBits)
                | (nodeId << sequenceBits)
                | sequence;
    }

    private long waitNextMillis(long currentTimestamp) {
        long timestamp = currentTimestamp;
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    @Scheduled(fixedRate = 1000) // Run every 1 second
    private void saveState() {
        // No lock needed here as we are just updating the atomic long and saving to disk
        // The nextId method only reads the atomic long
        long saveTime = currentTimestamp() + timeOffsetBufferMs;
        stateService.saveState(saveTime);
        lastSavedTimestamp.set(saveTime);
    }
}
