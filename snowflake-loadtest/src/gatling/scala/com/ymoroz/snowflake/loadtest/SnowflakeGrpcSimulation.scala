package com.ymoroz.snowflake.loadtest

import com.ymoroz.snowflake.proto.GenerateIdRequest
import com.ymoroz.snowflake.proto.SnowflakeServiceGrpc
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.grpc.ManagedChannel
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.util.InsecureTrustManagerFactory

import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt

class SnowflakeGrpcSimulation extends Simulation {

  private val host: String = System.getProperty("snowflake.host", "localhost")
  private val port: Int = Integer.getInteger("snowflake.port", 443)
  private val useTls: Boolean = java.lang.Boolean.parseBoolean(System.getProperty("snowflake.useTls", "true"))
  private val certPath: String = System.getProperty("snowflake.certPath", "certs/tls.crt")
  private val users: Int = Integer.getInteger("snowflake.users", 100)
  private val rampSeconds: Int = Integer.getInteger("snowflake.rampSeconds", 20)
  private val requestsPerUser: Int = Integer.getInteger("snowflake.requestsPerUser", 100)
  private val pauseMs: Int = Integer.getInteger("snowflake.pauseMs", 0)
  private val callDeadlineMs: Long = java.lang.Long.getLong("snowflake.callDeadlineMs", 1000L)

  @volatile private var channel: ManagedChannel = _
  @volatile private var stub: SnowflakeServiceGrpc.SnowflakeServiceBlockingStub = _

  private def createSslContext(): SslContext = {
    val certFile = new File(certPath)
    if (certFile.exists()) {
      GrpcSslContexts.forClient()
        .trustManager(certFile)
        .build()
    } else {
      // Fallback to insecure trust manager if cert not found (for development)
      GrpcSslContexts.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build()
    }
  }

  before {
    val channelBuilder = NettyChannelBuilder
      .forAddress(host, port)

    if (useTls) {
      channelBuilder
        .useTransportSecurity()
        .sslContext(createSslContext())
    } else {
      channelBuilder.usePlaintext()
    }

    channel = channelBuilder.build()
    stub = SnowflakeServiceGrpc.newBlockingStub(channel)
  }

  after {
    if (channel != null) {
      channel.shutdownNow()
      channel.awaitTermination(5, TimeUnit.SECONDS)
    }
  }

  private val scenarioDefinition: ScenarioBuilder =
    scenario("grpc-generate-id")
      .repeat(requestsPerUser) {
        exec { session =>
          val response = stub
            .withDeadlineAfter(callDeadlineMs, TimeUnit.MILLISECONDS)
            .generateId(GenerateIdRequest.newBuilder().build())
          if (response.getId > 0) session else session.markAsFailed
        }
          .exec(dummy("grpc-generate-id", 1))
          .pause(pauseMs.milliseconds)
      }

  setUp(
    scenarioDefinition.inject(rampUsers(users).during(rampSeconds.seconds))
  )
}
