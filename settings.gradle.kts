pluginManagement {
	val springBootVersion = providers.gradleProperty("springBootVersion").get()
	val springDependencyManagementVersion = providers.gradleProperty("springDependencyManagementVersion").get()
	val protobufGradlePluginVersion = providers.gradleProperty("protobufGradlePluginVersion").get()
	val gatlingPluginVersion = providers.gradleProperty("gatlingPluginVersion").get()

	plugins {
		id("org.springframework.boot") version springBootVersion
		id("io.spring.dependency-management") version springDependencyManagementVersion
		id("com.google.protobuf") version protobufGradlePluginVersion
		id("io.gatling.gradle") version gatlingPluginVersion
	}

	repositories {
		maven { url = uri("https://repo.spring.io/snapshot") }
		gradlePluginPortal()
	}
}

rootProject.name = "snowflake-id-service"

include("snowflake-proto")
include("snowflake-server")
include("snowflake-client")
include("snowflake-loadtest")
