package com.ymoroz.snowflake.id.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeStateServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveAndLoadState() {
        Path stateFile = tempDir.resolve("state.bin");
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());
        
        long saveTime = 123456789L;
        service.saveState(saveTime);
        
        long loadedTime = service.loadState(saveTime + 1000);
        assertEquals(saveTime, loadedTime);
    }

    @Test
    void testLoadStateWithWait() {
        Path stateFile = tempDir.resolve("state_wait.bin");
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());
        
        long savedTime = Instant.now().toEpochMilli() + 500; // 500ms in future
        service.saveState(savedTime);
        
        long startTime = Instant.now().toEpochMilli();
        long loadedTime = service.loadState(startTime);
        long endTime = Instant.now().toEpochMilli();
        
        assertEquals(savedTime, loadedTime);
        assertTrue(endTime - startTime >= 500, "Should have waited at least 500ms");
    }

    @Test
    void testLoadStateWithWaitInterrupted() {
        Path stateFile = tempDir.resolve("state_wait_interrupted.bin");
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());

        long savedTime = Instant.now().toEpochMilli() + 10000; // 10s in future
        service.saveState(savedTime);

        Thread.currentThread().interrupt();
        assertThrows(RuntimeException.class, () -> service.loadState(Instant.now().toEpochMilli()));
        assertTrue(Thread.interrupted(), "Interrupted flag should be cleared");
    }

    @Test
    void testLoadStateFileExistsButTooSmall() throws IOException {
        Path stateFile = tempDir.resolve("too_small.bin");
        Files.write(stateFile, new byte[]{1, 2, 3}); // Only 3 bytes
        
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());
        long loadedTime = service.loadState(1000L);
        
        assertEquals(-1L, loadedTime);
    }

    @Test
    void testLoadStateNoFile() {
        Path stateFile = tempDir.resolve("non_existent.bin");
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());
        
        long loadedTime = service.loadState(1000L);
        assertEquals(-1L, loadedTime);
        assertTrue(Files.exists(tempDir), "Should have created parent directories");
    }

    @Test
    void testLoadStateIOException() throws IOException {
        Path stateFile = tempDir.resolve("locked.bin");
        Files.createFile(stateFile);
        
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());
        
        // Make it a directory to cause IOException on open
        Files.delete(stateFile);
        Files.createDirectories(stateFile);
        
        assertThrows(RuntimeException.class, () -> service.loadState(1000L));
    }

    @Test
    void testSaveStateIOException() throws IOException {
        Path stateFile = tempDir.resolve("readonly_dir");
        Files.createDirectories(stateFile);
        // Pointing state file to a directory will cause IOException
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());
        
        assertThrows(RuntimeException.class, () -> service.saveState(1000L));
    }
    @Test
    void testLoadStateNoParentDir() throws IOException {
        Path stateFile = tempDir.resolve("new_dir/state.bin");
        // Ensure parent doesn't exist
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());
        
        long loadedTime = service.loadState(1000L);
        assertEquals(-1L, loadedTime);
        assertTrue(Files.exists(stateFile.getParent()), "Should have created parent directories");
    }

    @Test
    void testLoadStateWithParentDirExists() throws IOException {
        Path parentDir = tempDir.resolve("existing_dir");
        Files.createDirectories(parentDir);
        Path stateFile = parentDir.resolve("state.bin");
        SnowflakeStateServiceImpl service = new SnowflakeStateServiceImpl(stateFile.toString());
        
        long loadedTime = service.loadState(1000L);
        assertEquals(-1L, loadedTime);
    }
}
