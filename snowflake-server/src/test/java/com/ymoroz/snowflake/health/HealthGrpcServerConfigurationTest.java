package com.ymoroz.snowflake.health;

import io.grpc.Server;
import io.grpc.protobuf.services.HealthStatusManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class HealthGrpcServerConfigurationTest {

    @Test
    void testHealthStatusManagerBean() {
        HealthGrpcServerConfiguration config = new HealthGrpcServerConfiguration();
        HealthStatusManager manager = config.healthStatusManager();
        assertNotNull(manager);
    }

    @Test
    void testHealthGrpcServerBean() {
        HealthGrpcServerConfiguration config = new HealthGrpcServerConfiguration();
        HealthStatusManager manager = new HealthStatusManager();
        assertDoesNotThrow(() -> {
            Server server = config.healthGrpcServer(manager, 0);
            server.shutdownNow();
        });
    }

    @Test
    void testHealthGrpcServerShutdown() throws IOException {
        HealthGrpcServerConfiguration config = new HealthGrpcServerConfiguration();
        HealthStatusManager manager = config.healthStatusManager();
        
        // Start on random port
        Server server = config.healthGrpcServer(manager, 0);
        assertNotNull(server);
        
        config.shutdown();
    }

    @Test
    void testHealthGrpcServerShutdownWithNullServer() {
        HealthGrpcServerConfiguration config = new HealthGrpcServerConfiguration();
        config.shutdown();
    }

    @Test
    void testHealthGrpcServerShutdownForced() throws Exception {
        HealthGrpcServerConfiguration config = new HealthGrpcServerConfiguration();
        
        Server mockServer = mock(Server.class);
        when(mockServer.shutdown()).thenReturn(mockServer);
        when(mockServer.awaitTermination(anyLong(), any())).thenReturn(false);
        
        // Inject mock server via reflection
        java.lang.reflect.Field field = HealthGrpcServerConfiguration.class.getDeclaredField("healthServer");
        field.setAccessible(true);
        field.set(config, mockServer);
        
        config.shutdown();
        verify(mockServer).shutdownNow();
    }

    @Test
    void testHealthGrpcServerShutdownInterrupted() throws Exception {
        HealthGrpcServerConfiguration config = new HealthGrpcServerConfiguration();
        
        Server mockServer = mock(Server.class);
        when(mockServer.shutdown()).thenReturn(mockServer);
        when(mockServer.awaitTermination(anyLong(), any())).thenThrow(new InterruptedException());
        
        java.lang.reflect.Field field = HealthGrpcServerConfiguration.class.getDeclaredField("healthServer");
        field.setAccessible(true);
        field.set(config, mockServer);
        
        config.shutdown();
        verify(mockServer).shutdownNow();
        assertTrue(Thread.interrupted());
    }
}
