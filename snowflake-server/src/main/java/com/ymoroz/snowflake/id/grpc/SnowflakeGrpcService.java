package com.ymoroz.snowflake.id.grpc;

import com.ymoroz.snowflake.proto.GenerateIdRequest;
import com.ymoroz.snowflake.proto.GenerateIdResponse;
import com.ymoroz.snowflake.proto.SnowflakeServiceGrpc;
import com.ymoroz.snowflake.id.service.SnowflakeService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SnowflakeGrpcService extends SnowflakeServiceGrpc.SnowflakeServiceImplBase {

    private final SnowflakeService snowflakeService;

    @Override
    public void generateId(GenerateIdRequest request, StreamObserver<GenerateIdResponse> responseObserver) {
        long id = snowflakeService.nextId();
        GenerateIdResponse response = GenerateIdResponse.newBuilder()
                .setId(id)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
