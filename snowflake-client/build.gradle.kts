plugins {
    java
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
    implementation(project(":snowflake-proto"))
    implementation("io.grpc:grpc-netty-shaded:1.63.0")
    implementation("io.grpc:grpc-protobuf:1.63.0")
    implementation("io.grpc:grpc-stub:1.63.0")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    
    // Spring Boot Starter for Configuration Properties
    implementation("org.springframework.boot:spring-boot-starter:3.4.1")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.4.1")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
