package com.ymoroz.snowflake.snowflake.id.service;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeServiceConcurrencyTest {

    SnowflakeService service = new SnowflakeService("snowflake-1");

    @Test
    void nextIdUniqueness() throws Exception {
        int calls = 100_000;

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch start = new CountDownLatch(1);

        var ids = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        IntStream.range(0, calls).parallel().forEach(i -> pool.submit(() -> {
            try {
                start.await();
                long id = service.nextId();
                if (!ids.add(id)) throw new AssertionError("Duplicate id: " + id);
            } catch (Throwable t) {
                failures.add(t);
            }
        }));

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        assertThat(failures).withFailMessage(failures.toString()).isEmpty();
        assertThat(ids).hasSize(calls);
    }
}
