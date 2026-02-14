plugins {
    scala
    id("io.gatling.gradle")
}

group = "com.ymoroz.snowflake"
version = rootProject.findProperty("snowflakeLoadtestVersion") as String

val grpcVersion = rootProject.findProperty("grpcVersion") as String

repositories {
    mavenCentral()
}

dependencies {
    gatlingImplementation(project(":snowflake-proto"))
    gatlingImplementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    gatlingImplementation("io.grpc:grpc-protobuf:$grpcVersion")
    gatlingImplementation("io.grpc:grpc-stub:$grpcVersion")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
