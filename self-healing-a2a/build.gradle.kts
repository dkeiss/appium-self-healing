val springBootVersion: String by project
val springAiVersion: String by project
val appiumVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))

    implementation(project(":self-healing-core"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Needed because core's FailureContext/HealingResult expose Selenium's By on their signatures.
    implementation("io.appium:java-client:$appiumVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Needed only so the smoke test can subclass ChatClientLocatorHealer — its superclass references ChatClient.
    testImplementation("org.springframework.ai:spring-ai-client-chat")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
