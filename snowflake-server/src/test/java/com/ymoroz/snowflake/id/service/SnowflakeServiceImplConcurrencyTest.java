package com.ymoroz.snowflake.id.service;

import com.ymoroz.snowflake.id.config.SnowflakeProperties;
import com.ymoroz.snowflake.id.parser.NodeIdParser;
import com.ymoroz.snowflake.id.parser.NodeIdParserImpl;
import com.ymoroz.snowflake.id.state.SnowflakeStatePersistenceCoordinator;
import com.ymoroz.snowflake.id.state.SnowflakeStateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeServiceImplConcurrencyTest {

    private SnowflakeServiceImpl service;
    private final SnowflakeStateServiceImpl mockStateService = mock(SnowflakeStateServiceImpl.class);
    private final NodeIdParser nodeIdParser = new NodeIdParserImpl();

    @BeforeEach
    void setUp() {
        SnowflakeProperties snowflakeProperties = new SnowflakeProperties();
        snowflakeProperties.setHostname("snowflake-1");
        SnowflakeStatePersistenceCoordinator coordinator =
                new SnowflakeStatePersistenceCoordinator(mockStateService);
        service = new SnowflakeServiceImpl(snowflakeProperties, nodeIdParser, coordinator);
    }

    @Test
    void nextIdUniqueness() throws Exception {
        int calls = 100_000;

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch start = new CountDownLatch(1);

        var ids = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        // Start tasks
        for (int i = 0; i < calls; i++) {
             pool.submit(() -> {
                try {
                    start.await();
                    long id = service.nextId();
                    if (!ids.add(id)) throw new AssertionError("Duplicate id: " + id);
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        assertThat(failures).withFailMessage(failures.toString()).isEmpty();
        assertThat(ids).hasSize(calls);
    }
}
