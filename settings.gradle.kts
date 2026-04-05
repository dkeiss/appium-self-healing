pluginManagement {
    val springBootVersion: String by settings

    plugins {
        id("org.springframework.boot") version springBootVersion
    }
}

rootProject.name = "appium-self-healing"

include("backend")
include("self-healing-core")
include("integration-tests")
include("benchmark")
// include("android-app") // Separate Android build — see android-app/README.md
