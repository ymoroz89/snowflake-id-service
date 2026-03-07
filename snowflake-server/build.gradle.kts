plugins {
    java
    jacoco
    id("org.springframework.boot")
}

group = "com.ymoroz.snowflake"
version = rootProject.findProperty("snowflakeServerVersion") as String

val springGrpcVersion = rootProject.findProperty("springGrpcVersion") as String
val springBootVersion = rootProject.findProperty("springBootVersion") as String
val lombokVersion = rootProject.findProperty("lombokVersion") as String
val grpcVersion = rootProject.findProperty("grpcVersion") as String
val micrometerPrometheusVersion = rootProject.findProperty("micrometerPrometheusVersion") as String

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
    implementation(project(":snowflake-proto"))
    implementation("org.springframework.boot:spring-boot-starter:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
    implementation("org.springframework.grpc:spring-grpc-spring-boot-starter:$springGrpcVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus:$micrometerPrometheusVersion")
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testImplementation(project(":snowflake-client"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.mockito:mockito-inline:5.2.0")
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
