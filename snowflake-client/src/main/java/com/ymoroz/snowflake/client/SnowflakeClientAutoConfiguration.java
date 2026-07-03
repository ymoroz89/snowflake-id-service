package com.ymoroz.snowflake.client;

import com.ymoroz.snowflake.proto.SnowflakeServiceGrpc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SnowflakeClientProperties.class)
@ConditionalOnClass(SnowflakeServiceGrpc.class)
@ConditionalOnProperty(prefix = "snowflake.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SnowflakeClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SnowflakeClient snowflakeClient(SnowflakeClientProperties properties) {
        return new SnowflakeClient(properties);
    }
}
