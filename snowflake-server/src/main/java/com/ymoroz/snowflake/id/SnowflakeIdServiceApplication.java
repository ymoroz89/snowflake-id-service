package com.ymoroz.snowflake.id;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.grpc.server.autoconfigure.health.GrpcServerHealthAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.ymoroz.snowflake",
        exclude = GrpcServerHealthAutoConfiguration.class
)
@EnableScheduling
public class SnowflakeIdServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnowflakeIdServiceApplication.class, args);
    }
}
