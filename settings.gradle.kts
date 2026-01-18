pluginManagement {
	repositories {
		maven { url = uri("https://repo.spring.io/snapshot") }
		gradlePluginPortal()
	}
}
rootProject.name = "snowflake-id-service"

include("snowflake-proto")
include("snowflake-server")
include("snowflake-client")
