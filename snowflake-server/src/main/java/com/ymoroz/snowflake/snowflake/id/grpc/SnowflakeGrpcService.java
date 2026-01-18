package com.ymoroz.snowflake.snowflake.id.grpc;

import com.ymoroz.snowflake.proto.GenerateIdRequest;
import com.ymoroz.snowflake.proto.GenerateIdResponse;
import com.ymoroz.snowflake.proto.SnowflakeServiceGrpc;
import com.ymoroz.snowflake.snowflake.id.service.SnowflakeService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import lombok.RequiredArgsConstructor;

@GrpcService
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
