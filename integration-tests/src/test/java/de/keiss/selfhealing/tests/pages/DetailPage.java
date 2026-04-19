package de.keiss.selfhealing.tests.pages;

import de.keiss.selfhealing.core.driver.SelfHealingAppiumDriver;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

/**
 * Page Object for the per-leg detail information ("very-hard-navigation" track).
 *
 * In v1 the leg details (train number and platform) are rendered inline on each connection card, reachable without any
 * navigation. In v2 they live inside a ModalBottomSheet that is only shown after tapping the journey card, so the
 * locators must first be discovered after a click-driven navigation (different DOM, different ids):
 *
 * "connection_item" → "journey_card" (rename + triggers bottom-sheet) "leg_train_number" → "leg_item_0_train" (moved
 * into bottom sheet) "leg_platform" → "leg_item_0_platform" (moved into bottom sheet)
 */
@Component
@RequiredArgsConstructor
public class DetailPage {

    private static final By CONNECTION_ITEM = By.id("connection_item");
    private static final By LEG_TRAIN_NUMBER = By.id("leg_train_number");
    private static final By LEG_PLATFORM = By.id("leg_platform");

    private final SelfHealingAppiumDriver driver;

    public void tapFirstConnection() {
        WebElement first = driver.findElement(CONNECTION_ITEM);
        first.click();
    }

    public String getFirstLegTrainNumber() {
        return driver.findElementWithWait(LEG_TRAIN_NUMBER, 10).getText();
    }

    public String getFirstLegPlatform() {
        return driver.findElementWithWait(LEG_PLATFORM, 10).getText();
    }
}
