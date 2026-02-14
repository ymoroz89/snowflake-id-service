plugins {
    java
    jacoco
    id("io.spring.dependency-management")
}

group = "com.ymoroz.snowflake"
version = rootProject.findProperty("snowflakeClientVersion") as String

val grpcVersion = rootProject.findProperty("grpcVersion") as String
val springBootVersion = rootProject.findProperty("springBootVersion") as String

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    }
}

dependencies {
    implementation(project(":snowflake-proto"))
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    implementation("org.springframework.boot:spring-boot-starter")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
