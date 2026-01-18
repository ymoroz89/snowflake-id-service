package com.ymoroz.snowflake.snowflake.id;

import com.ymoroz.snowflake.snowflake.id.service.SnowflakeService;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executors;

@SpringBootApplication
public class SnowflakeIdServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SnowflakeIdServiceApplication.class, args);
	}

	@Bean
	public SnowflakeService snowflakeService() {
		return new SnowflakeService();
	}

	@Bean
	public GrpcServerConfigurer virtualThreadConfigurer() {
		return builder -> builder.executor(Executors.newVirtualThreadPerTaskExecutor());
	}
}
