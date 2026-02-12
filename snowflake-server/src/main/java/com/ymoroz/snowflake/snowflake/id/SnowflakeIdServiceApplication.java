package com.ymoroz.snowflake.snowflake.id;

import com.ymoroz.snowflake.snowflake.id.grpc.SnowflakeGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executors;

@SpringBootApplication
public class SnowflakeIdServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SnowflakeIdServiceApplication.class, args);
	}

	@Bean(initMethod = "start", destroyMethod = "shutdown")
	public Server grpcServer(SnowflakeGrpcService snowflakeGrpcService,
							 @Value("${grpc.server.port:9090}") int port) {
		return ServerBuilder.forPort(port)
				.addService(snowflakeGrpcService)
				.executor(Executors.newVirtualThreadPerTaskExecutor())
				.build();
	}
}
