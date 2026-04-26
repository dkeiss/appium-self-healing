val springBootVersion: String by project
val springAiVersion: String by project
val appiumVersion: String by project
val jsoupVersion: String by project
val jgitVersion: String by project
val githubApiVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-mistral-ai")
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")

    implementation("io.appium:java-client:$appiumVersion")
    implementation("org.jsoup:jsoup:$jsoupVersion")

    // Auto-Fix PR creation (Phase 5.2)
    implementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    implementation("org.kohsuke:github-api:$githubApiVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Gradle 9+ no longer auto-provides the JUnit Platform launcher; declare it explicitly.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
