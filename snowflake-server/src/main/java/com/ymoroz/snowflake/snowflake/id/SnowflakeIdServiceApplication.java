package com.ymoroz.snowflake.snowflake.id;

import com.ymoroz.snowflake.snowflake.id.grpc.SnowflakeGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executors;

@SpringBootApplication
public class SnowflakeIdServiceApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(SnowflakeIdServiceApplication.class, args);
		Server server = context.getBean(Server.class);
		try {
			server.awaitTermination();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for gRPC server termination", e);
		}
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
