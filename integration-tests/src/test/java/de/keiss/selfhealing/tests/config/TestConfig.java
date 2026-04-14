package de.keiss.selfhealing.tests.config;

import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.driver.SelfHealingAppiumDriver;
import de.keiss.selfhealing.core.driver.SourceCodeResolver;
import de.keiss.selfhealing.core.healing.HealingOrchestrator;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class TestConfig {

    @Value("${appium.url:http://localhost:4723}")
    private String appiumUrl;

    @Getter
    @Value("${app.package:de.keiss.selfhealing.app}")
    private String appPackage;

    @Value("${app.activity:de.keiss.selfhealing.app.MainActivity}")
    private String appActivity;

    @Value("${app.apk-path:}")
    private String apkPath;

    private final HealingOrchestrator healingOrchestrator;
    private final SelfHealingProperties selfHealingProperties;
    private SelfHealingAppiumDriver selfHealingDriver;
    private AppiumDriver appiumDriver;

    @Bean
    @Lazy
    public SelfHealingAppiumDriver getDriver() {
        if (selfHealingDriver == null) {
            selfHealingDriver = createSelfHealingDriver();
        }
        return selfHealingDriver;
    }

    private SelfHealingAppiumDriver createSelfHealingDriver() {
        UiAutomator2Options options = new UiAutomator2Options().setDeviceName("Android Emulator")
                .setAppPackage(appPackage).setAppActivity(appActivity).setAutomationName("UiAutomator2")
                .setNoReset(true).setAutoGrantPermissions(true).amend("appium:disableIdLocatorAutocompletion", true);

        // If an APK path is specified, install the app
        if (apkPath != null && !apkPath.isBlank()) {
            options.setApp(apkPath);
        }

        try {
            appiumDriver = new AndroidDriver(URI.create(appiumUrl).toURL(), options);
            appiumDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Appium URL: " + appiumUrl, e);
        }

        String sourceBase = selfHealingProperties.sourceBasePath() != null ? selfHealingProperties.sourceBasePath()
                : System.getProperty("user.dir");

        var sourceResolver = new SourceCodeResolver(Path.of(sourceBase));

        log.info("Created SelfHealingAppiumDriver (appium: {}, package: {}, maxRetries: {})", appiumUrl, appPackage,
                selfHealingProperties.maxRetries());

        return new SelfHealingAppiumDriver(appiumDriver, healingOrchestrator, sourceResolver,
                selfHealingProperties.maxRetries());
    }

    @PreDestroy
    public void cleanup() {
        if (appiumDriver != null) {
            log.info("Quitting Appium driver...");
            appiumDriver.quit();
        }
    }
}
