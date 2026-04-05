plugins {
    id("org.springframework.boot")
}

val springBootVersion: String by project
val springAiVersion: String by project
val cucumberVersion: String by project
val appiumVersion: String by project

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))

    implementation(project(":self-healing-core"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // HealingResult has a Selenium By field — javac needs the type on the test
    // classpath to resolve the canonical constructor call in collector tests, even
    // though we pass null and the benchmark's main code never references By.
    testCompileOnly("io.appium:java-client:$appiumVersion")
}
