package com.ymoroz.snowflake.health;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for the separate gRPC server that handles health checks.
 * This allows health checks to be served on a different port than the main service.
 * <p>
 * The health port is configurable via the {@code app.health.port} property (default: 9091).
 */
@Configuration
@Slf4j
public class HealthGrpcServerConfiguration {

    private Server healthServer;

    @Bean
    public HealthStatusManager healthStatusManager() {
        return new HealthStatusManager();
    }

    @Bean
    public Server healthGrpcServer(
            HealthStatusManager healthStatusManager,
            @Value("${app.health.port:9091}") int healthPort) throws IOException {

        healthServer = Grpc.newServerBuilderForPort(healthPort, InsecureServerCredentials.create())
                .addService(healthStatusManager.getHealthService())
                .addService(ProtoReflectionServiceV1.newInstance())
                .build()
                .start();

        log.info("Health gRPC server started on port {}", healthPort);
        return healthServer;
    }

    @PreDestroy
    public void shutdown() {
        if (healthServer != null) {
            log.info("Shutting down health gRPC server");
            healthServer.shutdown();
            try {
                if (!healthServer.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Health gRPC server did not shut down gracefully, forcing shutdown");
                    healthServer.shutdownNow();
                }
            } catch (InterruptedException e) {
                healthServer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}