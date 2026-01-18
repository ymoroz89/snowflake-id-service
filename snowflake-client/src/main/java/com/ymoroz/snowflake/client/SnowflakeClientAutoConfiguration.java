package com.ymoroz.snowflake.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SnowflakeClientProperties.class)
public class SnowflakeClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SnowflakeClient snowflakeClient(SnowflakeClientProperties properties) {
        return new SnowflakeClient(properties);
    }
}
