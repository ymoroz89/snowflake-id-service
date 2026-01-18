package com.ymoroz.snowflake.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SnowflakeClientAutoConfiguration.class));

    @Test
    void shouldRegisterSnowflakeClientWithDefaultProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SnowflakeClient.class);
            assertThat(context).hasSingleBean(SnowflakeClientProperties.class);
            SnowflakeClientProperties properties = context.getBean(SnowflakeClientProperties.class);
            assertThat(properties.getHost()).isEqualTo("localhost");
            assertThat(properties.getPort()).isEqualTo(9090);
        });
    }

    @Test
    void shouldRegisterSnowflakeClientWithCustomProperties() {
        contextRunner.withPropertyValues(
                "snowflake.client.host=otherhost",
                "snowflake.client.port=1234"
        ).run(context -> {
            assertThat(context).hasSingleBean(SnowflakeClient.class);
            SnowflakeClientProperties properties = context.getBean(SnowflakeClientProperties.class);
            assertThat(properties.getHost()).isEqualTo("otherhost");
            assertThat(properties.getPort()).isEqualTo(1234);
        });
    }
}
