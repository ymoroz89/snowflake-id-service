package com.ymoroz.snowflake.snowflake.id;

import com.ymoroz.snowflake.snowflake.id.service.SnowflakeService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SnowflakeIdServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SnowflakeIdServiceApplication.class, args);
	}

	@Bean
	public SnowflakeService snowflakeService() {
		return new SnowflakeService();
	}
}
