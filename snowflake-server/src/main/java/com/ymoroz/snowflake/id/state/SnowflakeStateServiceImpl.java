package com.ymoroz.snowflake.id.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Service for managing Snowflake ID service state persistence.
 * 
 * <p>Handles loading and saving of timestamp state to prevent duplicate IDs
 * across service restarts. Provides thread-safe state management with
 * proper error handling and logging.
 * 
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>State file persistence for timestamp continuity</li>
 *   <li>Clock drift protection with automatic recovery</li>
 *   <li>Thread-safe file operations</li>
 *   <li>Comprehensive error handling and logging</li>
 * </ul>
 * 
 * <p>This service follows the Single Responsibility Principle by focusing solely
 * on state management, and uses Dependency Injection for better testability
 * and maintainability.
 * 
 * @author Yuriy Moroz
 * @since 1.0.0
 */
@Slf4j
@Service
public class SnowflakeStateServiceImpl implements SnowflakeStateService {
    
    private final Path stateFile;

    public SnowflakeStateServiceImpl(@Value("${state.file}") final String stateFilePath) {
        this.stateFile = Path.of(stateFilePath);
    }

    /**
     * Loads the last saved timestamp from the state file.
     * 
     * <p>If the state file exists, reads the saved timestamp and ensures
     * the current system time is not behind it. If the system time is behind,
     * waits for the time to catch up to prevent duplicate ID generation.
     * 
     * @return the last saved timestamp, or -1 if no state file exists
     * @throws RuntimeException if there's an error reading the state file
     */
    @Override
    public long loadState(long currentTime) {
        if (Files.exists(stateFile)) {
            try (FileChannel channel = FileChannel.open(stateFile, StandardOpenOption.READ)) {
                ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                int bytesRead = channel.read(buffer);
                if (bytesRead >= Long.BYTES) {
                    buffer.flip();
                    long savedTime = buffer.getLong();
                    log.info("Loaded last saved timestamp: {}", savedTime);
                    if (currentTime < savedTime) {
                        long waitMs = savedTime - currentTime;
                        log.warn("Current time {} is behind last saved time {}. Waiting {} ms...", currentTime, savedTime, waitMs);
                        try {
                            Thread.sleep(waitMs + 100); // Wait a bit more to be safe
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted while waiting for time to catch up", e);
                        }
                    }
                    return savedTime;
                }
            } catch (IOException e) {
                log.error("Failed to load state from file: {}", stateFile, e);
                throw new RuntimeException("Failed to load state", e);
            }
        } else {
            log.info("No state file found at {}. Starting fresh.", stateFile);
            try {
                if (stateFile.getParent() != null) {
                    Files.createDirectories(stateFile.getParent());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to create state directory", e);
            }
        }
        return -1L;
    }

    /**
     * Saves the reserved timestamp to the state file.
     * 
     * <p>The coordinator computes and coalesces the reserved timestamp before handing it to this service.
     * 
     * @param saveTime the reserved timestamp to save
     * @throws RuntimeException if there's an error writing the state file
     */
    @Override
    public void saveState(long saveTime) {
        // We save current + buffer to "reserve" this time window
        log.debug("Saving state timestamp {} to file {}", saveTime, stateFile);

        try (FileChannel channel = FileChannel.open(stateFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.DSYNC)) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(saveTime);
            buffer.flip();
            channel.write(buffer, 0); // Write at the beginning
            log.debug("Successfully saved state to file");
        } catch (IOException e) {
            log.error("Failed to save state to file: {}", stateFile, e);
            throw new RuntimeException("Failed to save state", e);
        }
    }

}
