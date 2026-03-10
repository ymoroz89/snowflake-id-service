package com.ymoroz.snowflake.id.grpc;

import com.ymoroz.snowflake.id.service.SnowflakeService;
import com.ymoroz.snowflake.proto.GenerateIdRequest;
import com.ymoroz.snowflake.proto.GenerateIdResponse;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnowflakeGrpcServiceTest {

    @Test
    void shouldGenerateIdAndSendResponse() {
        SnowflakeService snowflakeService = mock(SnowflakeService.class);
        when(snowflakeService.nextId()).thenReturn(42L);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SnowflakeGrpcService grpcService = new SnowflakeGrpcService(snowflakeService, meterRegistry);
        CapturingObserver observer = new CapturingObserver();

        grpcService.generateId(GenerateIdRequest.getDefaultInstance(), observer);

        assertEquals(1, observer.responses.size());
        assertEquals(42L, observer.responses.getFirst().getId());
        assertTrue(observer.completed);
        assertNull(observer.error);
        verify(snowflakeService).nextId();

        Counter generatedIdsCounter = meterRegistry.get("snowflake.ids.generated.total").counter();
        Timer idGenerationLatency = meterRegistry.get("snowflake.ids.generation.latency").timer();

        assertEquals(1.0, generatedIdsCounter.count(), 0.0);
        assertEquals(1L, idGenerationLatency.count());
    }

    @Test
    void shouldPropagateExceptionAndNotCompleteResponse() {
        SnowflakeService snowflakeService = mock(SnowflakeService.class);
        when(snowflakeService.nextId()).thenThrow(new IllegalStateException("Generation failed"));

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SnowflakeGrpcService grpcService = new SnowflakeGrpcService(snowflakeService, meterRegistry);
        CapturingObserver observer = new CapturingObserver();

        assertThrows(IllegalStateException.class,
                () -> grpcService.generateId(GenerateIdRequest.getDefaultInstance(), observer));

        assertTrue(observer.responses.isEmpty());
        assertFalse(observer.completed);
        assertNull(observer.error);
        verify(snowflakeService).nextId();

        Counter generatedIdsCounter = meterRegistry.get("snowflake.ids.generated.total").counter();
        Timer idGenerationLatency = meterRegistry.get("snowflake.ids.generation.latency").timer();

        assertEquals(0.0, generatedIdsCounter.count(), 0.0);
        assertEquals(1L, idGenerationLatency.count());
    }

    private static final class CapturingObserver implements StreamObserver<GenerateIdResponse> {

        private final List<GenerateIdResponse> responses = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(final GenerateIdResponse value) {
            responses.add(value);
        }

        @Override
        public void onError(final Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
