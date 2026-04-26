plugins {
    java
    jacoco
    id("org.springframework.boot") apply false
    id("com.diffplug.spotless") version "7.0.4"
    id("org.sonarqube") version "6.3.1.5724"
}

allprojects {
    group = "de.keiss.selfhealing"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
}

spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**")
        eclipse("4.34").configFile("config/eclipse-formatter.xml")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    val lombokVersion: String by project

    dependencies {
        compileOnly("org.projectlombok:lombok:$lombokVersion")
        annotationProcessor("org.projectlombok:lombok:$lombokVersion")
        testCompileOnly("org.projectlombok:lombok:$lombokVersion")
        testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.withType<JacocoReport>())
    }

    tasks.withType<JacocoReport> {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(false)
        }
    }
}

sonar {
    properties {
        property("sonar.projectKey", "appium-self-healing")
        property("sonar.projectName", "appium-self-healing")
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
        property("sonar.token", System.getenv("SONAR_TOKEN") ?: "")
        property("sonar.coverage.jacoco.xmlReportPaths",
            subprojects.joinToString(",") { "${it.layout.buildDirectory.get().asFile}/reports/jacoco/test/jacocoTestReport.xml" })
        property("sonar.exclusions", "**/build/**,**/generated/**,android-app/**")
    }
}

// ---------------------------------------------------------------------------
// Benchmark orchestration
// ---------------------------------------------------------------------------
// Runs the integration tests once per LLM provider with benchmark collection
// enabled, then aggregates all resulting fragments into a comparison report.
//
// Usage:
//   ./gradlew benchmarkAll                                # all providers
//   ./gradlew benchmarkAll -PllmProviders=anthropic,local # subset
//
// Each provider run requires the corresponding API key to be set in the
// environment (ANTHROPIC_API_KEY, OPENAI_API_KEY, MISTRAL_API_KEY) or a running
// local LM Studio instance for the "local" provider.
//
// Note: the property name intentionally is NOT "providers" to avoid a clash
// with Gradle's built-in `Project.providers` (ProviderFactory).
// ---------------------------------------------------------------------------
val defaultLlmProviders = listOf("anthropic", "openai", "mistral", "local")

val benchmarkProviders: List<String> = (project.findProperty("llmProviders") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() }
    ?: defaultLlmProviders

val benchmarkFragmentsDir = rootProject.layout.buildDirectory.dir("reports/benchmark")

val benchmarkRuns = benchmarkProviders.map { provider ->
    tasks.register<GradleBuild>("benchmarkRun_${provider}") {
        group = "benchmark"
        description = "Runs the integration test suite for LLM provider '$provider' with benchmark collection enabled"

        tasks = listOf(":integration-tests:test")
        startParameter.projectProperties = mapOf(
            "providerProfile" to provider
        )
        startParameter.systemPropertiesArgs = mapOf(
            "benchmark.enabled" to "true",
            "benchmark.output-dir" to benchmarkFragmentsDir.get().asFile.absolutePath,
            "benchmark.track-name" to "full-suite",
            "benchmark.difficulty" to "MEDIUM",
            "spring.profiles.active" to provider
        )
    }
}

tasks.register("benchmarkAll") {
    group = "benchmark"
    description = "Runs all configured LLM providers through the integration test suite and aggregates a comparison report"

    dependsOn(benchmarkRuns)
    finalizedBy(":benchmark:bootRun")

    doFirst {
        logger.lifecycle("Running benchmark for providers: $benchmarkProviders")
        logger.lifecycle("Fragments will be written to: ${benchmarkFragmentsDir.get().asFile.absolutePath}")
    }
}
