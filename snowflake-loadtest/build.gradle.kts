plugins {
    scala
    id("io.gatling.gradle") version "3.13.4"
}

group = "com.ymoroz.snowflake"
version = rootProject.findProperty("snowflakeLoadtestVersion") as String

repositories {
    mavenCentral()
}

dependencies {
    gatlingImplementation(project(":snowflake-proto"))
    gatlingImplementation("io.grpc:grpc-netty-shaded:1.78.0")
    gatlingImplementation("io.grpc:grpc-protobuf:1.78.0")
    gatlingImplementation("io.grpc:grpc-stub:1.78.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
