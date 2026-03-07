package com.ymoroz.snowflake.id.grpc;

import com.ymoroz.snowflake.id.service.SnowflakeService;
import com.ymoroz.snowflake.proto.GenerateIdRequest;
import com.ymoroz.snowflake.proto.GenerateIdResponse;
import com.ymoroz.snowflake.proto.SnowflakeServiceGrpc;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class SnowflakeGrpcService extends SnowflakeServiceGrpc.SnowflakeServiceImplBase {

    private final SnowflakeService snowflakeService;
    private final Counter generatedIdsCounter;
    private final Timer idGenerationLatency;

    public SnowflakeGrpcService(final SnowflakeService snowflakeService, final MeterRegistry meterRegistry) {
        this.snowflakeService = snowflakeService;
        this.generatedIdsCounter = meterRegistry.counter("snowflake.ids.generated.total");
        this.idGenerationLatency = meterRegistry.timer("snowflake.ids.generation.latency");
    }

    @Override
    public void generateId(GenerateIdRequest request, StreamObserver<GenerateIdResponse> responseObserver) {
        idGenerationLatency.record(() -> {
            long id = snowflakeService.nextId();
            generatedIdsCounter.increment();
            GenerateIdResponse response = GenerateIdResponse.newBuilder()
                    .setId(id)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        });
    }
}
