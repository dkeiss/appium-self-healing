package de.keiss.selfhealing.tests.steps;

import de.keiss.selfhealing.core.driver.SelfHealingAppiumDriver;
import de.keiss.selfhealing.tests.config.TestConfig;
import io.appium.java_client.android.AndroidDriver;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.de.Angenommen;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

@Slf4j
@RequiredArgsConstructor
public class AppSteps {

    private final TestConfig testConfig;

    @SuppressWarnings("java:S2925") // Thread.sleep is necessary for Appium app lifecycle sync
    @Angenommen("die App ist gestartet")
    public void appIsStarted() {
        log.info("Launching app on device...");
        var delegate = testConfig.getDriver().getDelegate();
        if (delegate instanceof AndroidDriver androidDriver) {
            // Terminate and restart the app to always start on the search screen.
            // This is needed because v2 uses navigation: after a search, the app
            // is on the results screen. Without restart, subsequent scenarios
            // would fail to find search screen elements.
            try {
                androidDriver.terminateApp(testConfig.getAppPackage());
                Thread.sleep(500);
            } catch (Exception e) {
                log.debug("App was not running: {}", e.getMessage());
            }
            androidDriver.activateApp(testConfig.getAppPackage());
            try {
                Thread.sleep(1000); // Wait for app launch and Compose rendering
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Before
    public void setUp() {
        log.info("Setting up test environment...");
    }

    @After
    public void tearDown(Scenario scenario) {
        log.info("Cleaning up after scenario...");

        // Attach healing screenshots (captured right after each heal with red highlight border)
        try {
            for (SelfHealingAppiumDriver.HealingScreenshot hs : testConfig.getDriver()
                    .getAndClearHealingScreenshots()) {
                scenario.attach(hs.data(), "image/png", hs.description());
                log.info("Attached healing screenshot: {}", hs.description());
            }
        } catch (Exception e) {
            log.warn("Could not attach healing screenshots: {}", e.getMessage());
        }

        // Attach final scenario screenshot
        try {
            var delegate = testConfig.getDriver().getDelegate();
            byte[] screenshot = ((TakesScreenshot) delegate).getScreenshotAs(OutputType.BYTES);
            scenario.attach(screenshot, "image/png", scenario.getName());
        } catch (Exception e) {
            log.warn("Could not attach screenshot to report: {}", e.getMessage());
        }
    }
}
