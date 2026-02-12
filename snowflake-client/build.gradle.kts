plugins {
    java
    jacoco
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
    implementation("io.grpc:grpc-netty-shaded:1.78.0")
    implementation("io.grpc:grpc-protobuf:1.78.0")
    implementation("io.grpc:grpc-stub:1.78.0")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    
    // Spring Boot Starter for Configuration Properties
    implementation("org.springframework.boot:spring-boot-starter:4.0.1")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.0.1")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy("jacocoTestReport")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
}
