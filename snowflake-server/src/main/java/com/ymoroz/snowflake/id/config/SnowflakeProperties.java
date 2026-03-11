package com.ymoroz.snowflake.id.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Snowflake ID generation service.
 * 
 * <p>These properties control the behavior of the distributed ID generation algorithm,
 * including the bit allocation for timestamp, node ID, and sequence components.
 */
@Component
@ConfigurationProperties(prefix = "snowflake")
public class SnowflakeProperties {
    
    /**
     * Custom epoch in milliseconds since Unix epoch.
     * Default: 1420070400000L (January 1, 2015 00:00:00 UTC)
     */
    private long customEpoch = 1420070400000L;
    
    /**
     * Number of bits allocated for node identifier.
     * Default: 10 bits (supports up to 1024 nodes)
     */
    private int nodeIdBits = 10;
    
    /**
     * Number of bits allocated for sequence number.
     * Default: 12 bits (supports 4096 IDs per millisecond per node)
     */
    private int sequenceBits = 12;
    
    /**
     * Time offset buffer in milliseconds for safety.
     * Default: 3000ms (3 seconds)
     */
    private long timeOffsetBufferMs = 3000L;

    /**
     * Maximum system clock rollback (in milliseconds) that will be handled by waiting.
     * Larger rollback values fail fast to prevent unpredictable latency spikes.
     * Default: 10ms.
     */
    private long maxClockBackwardWaitMs = 10L;
    
    private String hostname;

    // Getters and setters
    
    public long getCustomEpoch() {
        return customEpoch;
    }

    public void setCustomEpoch(long customEpoch) {
        this.customEpoch = customEpoch;
    }

    public int getNodeIdBits() {
        return nodeIdBits;
    }

    public void setNodeIdBits(int nodeIdBits) {
        this.nodeIdBits = nodeIdBits;
    }

    public int getSequenceBits() {
        return sequenceBits;
    }

    public void setSequenceBits(int sequenceBits) {
        this.sequenceBits = sequenceBits;
    }

    public long getTimeOffsetBufferMs() {
        return timeOffsetBufferMs;
    }

    public void setTimeOffsetBufferMs(long timeOffsetBufferMs) {
        this.timeOffsetBufferMs = timeOffsetBufferMs;
    }

    public long getMaxClockBackwardWaitMs() {
        return maxClockBackwardWaitMs;
    }

    public void setMaxClockBackwardWaitMs(long maxClockBackwardWaitMs) {
        this.maxClockBackwardWaitMs = maxClockBackwardWaitMs;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
}
