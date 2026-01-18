plugins {
    java
    id("com.google.protobuf") version "0.9.4"
    `maven-publish`
}

group = "com.ymoroz.snowflake"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.grpc:grpc-protobuf:1.78.0")
    implementation("io.grpc:grpc-stub:1.78.0")
    implementation("com.google.protobuf:protobuf-java:4.29.3")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3"
    }
    plugins {
        register("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.78.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                register("grpc")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
