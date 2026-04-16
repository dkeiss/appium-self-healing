val springBootVersion: String by project
val springAiVersion: String by project
val appiumVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-mistral-ai")

    implementation("io.appium:java-client:$appiumVersion")
    implementation("org.jsoup:jsoup:1.22.1")

    // Auto-Fix PR creation (Phase 5.2)
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.6.0.202603022253-r")
    implementation("org.kohsuke:github-api:1.330")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Gradle 9+ no longer auto-provides the JUnit Platform launcher; declare it explicitly.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
