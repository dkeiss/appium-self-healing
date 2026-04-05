package de.keiss.selfhealing.tests.pages;

import de.keiss.selfhealing.core.driver.SelfHealingAppiumDriver;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

/**
 * Page Object for the connection search screen.
 *
 * Locators target the v1 layout using Compose testTag attributes. In Appium, Compose testTags are exposed as the
 * resource-id suffix.
 *
 * When running against v2, these locators will break because v2 uses: - "departure_station" instead of "input_from" -
 * "arrival_station" instead of "input_to" - "fab_search" instead of "btn_search" (also different widget type)
 */
@Component
@RequiredArgsConstructor
public class SearchPage {

    // v1 locators — these WILL break when running against v2
    private static final By INPUT_FROM = By.id("input_from");
    private static final By INPUT_TO = By.id("input_to");
    private static final By BTN_SEARCH = By.id("btn_search");

    private final SelfHealingAppiumDriver driver;

    public void enterDeparture(String station) {
        WebElement fromField = driver.findElement(INPUT_FROM);
        fromField.click();
        fromField.clear();
        fromField.sendKeys(station);
    }

    public void enterDestination(String station) {
        WebElement toField = driver.findElement(INPUT_TO);
        toField.click();
        toField.clear();
        toField.sendKeys(station);
    }

    public void clickSearch() {
        // Hide keyboard before clicking search — in v2 the search button is a FAB
        // which may not be rendered while the soft keyboard is shown
        try {
            ((io.appium.java_client.android.AndroidDriver) driver.getDelegate()).hideKeyboard();
        } catch (Exception ignored) {
            // keyboard might not be shown
        }
        driver.findElement(BTN_SEARCH).click();
    }
}
