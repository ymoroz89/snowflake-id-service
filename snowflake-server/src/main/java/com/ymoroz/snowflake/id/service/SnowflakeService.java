package com.ymoroz.snowflake.id.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ToString
@Service
@Slf4j
public class SnowflakeService {
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long DEFAULT_CUSTOM_EPOCH = 1420070400000L;
    private static final long TIME_OFFSET_BUFFER_MS = 3000L; // 3 seconds buffer

    private final long nodeId;
    private final Path stateFilePath;
    private final Lock lock = new ReentrantLock();
    
    private long lastTimestamp = -1L;
    private long sequence = 0L;
    private long lastSavedTimestamp = -1L;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SnowflakeService(@Value("${HOSTNAME:snowflake-0}") String hostname,
                            @Value("${snowflake.state.file:/data/snowflake.state}") String stateFile) {
        this.nodeId = extractOrdinal(hostname);
        this.stateFilePath = Path.of(stateFile);
    }

    @PostConstruct
    public void init() {
        loadState();
        // Start periodic state saving every 1 second
        scheduler.scheduleAtFixedRate(this::saveState, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        saveState(); // Save one last time on shutdown
    }

    public long nextId() {
        lock.lock();
        try {
            long currentTimestamp = timestamp();

            // Ensure we don't generate IDs ahead of our reserved time window
            // This is a safety check, though loadState handles the initial catch-up
            if (currentTimestamp > lastSavedTimestamp) {
                // If we drifted past our reservation (e.g. scheduler stalled), save immediately
                saveState();
            }

            if (currentTimestamp < lastTimestamp) {
                log.error("Invalid System Clock! Current: {}, Last: {}", currentTimestamp, lastTimestamp);
                throw new IllegalStateException("Invalid System Clock!");
            }

            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
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
        return Instant.now().toEpochMilli() - DEFAULT_CUSTOM_EPOCH;
    }

    private long waitNextMillis(long currentTimestamp) {
        long timestamp = currentTimestamp;
        while (timestamp <= lastTimestamp) {
            timestamp = timestamp();
        }
        return timestamp;
    }

    private void loadState() {
        if (Files.exists(stateFilePath)) {
            try (FileChannel channel = FileChannel.open(stateFilePath, StandardOpenOption.READ)) {
                ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                int bytesRead = channel.read(buffer);
                if (bytesRead >= Long.BYTES) {
                    buffer.flip();
                    long savedTime = buffer.getLong();
                    log.info("Loaded last saved timestamp: {}", savedTime);
                    
                    long current = timestamp();
                    if (current < savedTime) {
                        long waitMs = savedTime - current;
                        log.warn("Current time {} is behind last saved time {}. Waiting {} ms...", current, savedTime, waitMs);
                        try {
                            Thread.sleep(waitMs + 100); // Wait a bit more to be safe
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted while waiting for time to catch up", e);
                        }
                    }
                    // Initialize lastTimestamp to ensure monotonicity
                    lastTimestamp = Math.max(lastTimestamp, savedTime);
                    lastSavedTimestamp = savedTime;
                }
            } catch (IOException e) {
                log.error("Failed to load state from file: {}", stateFilePath, e);
                throw new RuntimeException("Failed to load state", e);
            }
        } else {
            log.info("No state file found at {}. Starting fresh.", stateFilePath);
            try {
                if (stateFilePath.getParent() != null) {
                    Files.createDirectories(stateFilePath.getParent());
                }
            } catch (IOException e) {
                 throw new RuntimeException("Failed to create state directory", e);
            }
        }
    }

    private void saveState() {
        long current = timestamp();
        // We save current + buffer to "reserve" this time window
        long saveTime = current + TIME_OFFSET_BUFFER_MS;
        
        try (FileChannel channel = FileChannel.open(stateFilePath, 
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.DSYNC)) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(saveTime);
            buffer.flip();
            channel.write(buffer, 0); // Write at the beginning
            lastSavedTimestamp = saveTime;
        } catch (IOException e) {
            log.error("Failed to save state to file: {}", stateFilePath, e);
        }
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