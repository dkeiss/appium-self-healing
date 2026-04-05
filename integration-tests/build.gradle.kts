val springBootVersion: String by project
val springAiVersion: String by project
val cucumberVersion: String by project
val appiumVersion: String by project

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))

    testImplementation(project(":self-healing-core"))
    testImplementation(project(":benchmark"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.appium:java-client:$appiumVersion")

    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-spring:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("org.junit.platform:junit-platform-suite")
}

tasks.test {
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.plugin", "pretty,html:build/reports/cucumber.html,json:build/reports/cucumber.json")

    // Show Spring Boot / self-healing logs in test output
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }

    // Forward benchmark.* system properties from the Gradle invocation (-D flags)
    // so the in-process test JVM can see them and activate HealingMetricsCollector.
    System.getProperties()
        .stringPropertyNames()
        .filter { it.startsWith("benchmark.") }
        .forEach { systemProperty(it, System.getProperty(it)) }
}
