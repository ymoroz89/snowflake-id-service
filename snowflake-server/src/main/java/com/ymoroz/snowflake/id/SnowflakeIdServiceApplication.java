package com.ymoroz.snowflake.id;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.grpc.server.autoconfigure.health.GrpcServerHealthAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = GrpcServerHealthAutoConfiguration.class)
@ComponentScan(basePackages = {
    "com.ymoroz.snowflake.id",
    "com.ymoroz.snowflake.health"
})
public class SnowflakeIdServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnowflakeIdServiceApplication.class, args);
    }
}
