package com.ymoroz.snowflake.id.service;

import com.ymoroz.snowflake.id.config.SnowflakeProperties;
import com.ymoroz.snowflake.id.parser.NodeIdParser;
import com.ymoroz.snowflake.id.state.StatePersistenceCoordinator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

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
    private final NodeIdParser nodeIdParser;
    private final SnowflakeProperties snowflakeProperties;
    private final StatePersistenceCoordinator statePersistenceCoordinator;
    private final long nodeId;
    private final long maxSequence;
    private final int sequenceBits;
    private final int timestampShiftBits;
    private final long maxClockBackwardWaitMs;
    private final LongSupplier currentTimeMillisSupplier;
    private final ReentrantLock lock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    @Autowired
    public SnowflakeServiceImpl(final SnowflakeProperties snowflakeProperties,
                                final NodeIdParser nodeIdParser,
                                final StatePersistenceCoordinator statePersistenceCoordinator) {
        this(snowflakeProperties, nodeIdParser, statePersistenceCoordinator, System::currentTimeMillis);
    }

    SnowflakeServiceImpl(final SnowflakeProperties snowflakeProperties,
                         final NodeIdParser nodeIdParser,
                         final StatePersistenceCoordinator statePersistenceCoordinator,
                         final LongSupplier currentTimeMillisSupplier) {
        this.sequenceBits = snowflakeProperties.getSequenceBits();
        this.nodeIdParser = nodeIdParser;
        this.maxSequence = (1L << sequenceBits) - 1;
        this.timestampShiftBits = snowflakeProperties.getNodeIdBits() + sequenceBits;
        this.maxClockBackwardWaitMs = snowflakeProperties.getMaxClockBackwardWaitMs();
        this.nodeId = parseNodeId(snowflakeProperties.getHostname(), snowflakeProperties.getNodeIdBits());
        this.snowflakeProperties = snowflakeProperties;
        this.lock = new ReentrantLock();
        this.currentTimeMillisSupplier = Objects.requireNonNull(currentTimeMillisSupplier, "currentTimeMillisSupplier must not be null");
        this.statePersistenceCoordinator = Objects.requireNonNull(statePersistenceCoordinator, "statePersistenceCoordinator must not be null");
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
        long currentTime = currentTimeMillisSupplier.getAsLong() - snowflakeProperties.getCustomEpoch();
        long loadedState = statePersistenceCoordinator.initialize(currentTime);
        lastTimestamp = Math.max(lastTimestamp, loadedState);
    }

    @PreDestroy
    public void destroy() {
        statePersistenceCoordinator.persist(currentTimestamp());
        statePersistenceCoordinator.shutdown();
    }

    @Override
    public long nextId() {
        long generatedTimestamp;
        long generatedId;

        lock.lock();
        try {
            long currentTimestamp = currentTimestamp();
            currentTimestamp = validateSystemClock(currentTimestamp);

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
            generatedTimestamp = currentTimestamp;
            generatedId = buildId(currentTimestamp);
        } finally {
            lock.unlock();
        }

        statePersistenceCoordinator.persist(generatedTimestamp);
        return generatedId;
    }

    private long currentTimestamp() {
        return currentTimeMillisSupplier.getAsLong() - snowflakeProperties.getCustomEpoch();
    }

    private long validateSystemClock(long currentTimestamp) {
        if (currentTimestamp >= lastTimestamp) {
            return currentTimestamp;
        }

        long clockBackwardsMs = lastTimestamp - currentTimestamp;
        if (clockBackwardsMs <= maxClockBackwardWaitMs) {
            log.warn("System clock moved backwards by {} ms. Waiting for clock recovery.", clockBackwardsMs);
            return waitUntilTimestamp(lastTimestamp);
        }

        log.error("Invalid System Clock! Current: {}, Last: {}, Backwards by {} ms",
                currentTimestamp, lastTimestamp, clockBackwardsMs);
        throw new IllegalStateException("Invalid System Clock!");
    }

    private long buildId(long currentTimestamp) {
        return (currentTimestamp << timestampShiftBits)
                | (nodeId << sequenceBits)
                | sequence;
    }

    private long waitUntilTimestamp(long targetTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp < targetTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    private long waitNextMillis(long currentTimestamp) {
        long timestamp = currentTimestamp;
        while (timestamp <= lastTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    /**
     * <p> Why it still matters even with nextId() calling persist(...):
     * <ul>
     *   <li>It provides periodic retry if async state save failed earlier and traffic later drops.</li>
     *   <li>It reduces the crash window where generated IDs are ahead of the persisted timestamp.</li>
     *   <li>It keeps state persistence alive even during low/idle request periods.</li>
     * </ul>
     */
    @Scheduled(fixedRate = 1000)
    void saveState() {
        statePersistenceCoordinator.persist(currentTimestamp());
    }
}
