plugins {
    java
    pmd
}

allprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "pmd")

    pmd {
        isIgnoreFailures = false
        toolVersion = "7.0.0"
        ruleSets = listOf("category/java/errorprone.xml", "category/java/bestpractices.xml")
    }

    tasks.withType<Pmd>().configureEach {
        if (name.contains("Test")) {
            exclude("**/*")
        }
    }

    tasks.withType<Test>().configureEach {
        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    }

    if (name == "snowflake-proto") {
        tasks.withType<Pmd>().configureEach {
            exclude("**/com/ymoroz/snowflake/proto/**")
        }
    }
}
