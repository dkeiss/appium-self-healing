package de.keiss.selfhealing.tests.pages;

import de.keiss.selfhealing.core.driver.SelfHealingAppiumDriver;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Page Object for the connection results screen.
 *
 * Locators target the v1 layout. In v2, the results are on a separate screen with different IDs: - "results_container"
 * instead of "connection_list" - "journey_card" instead of "connection_item" - "label_departure" instead of "text_from"
 * - "label_arrival" instead of "text_to" - "label_changes" instead of "text_transfers" - "label_fare" instead of
 * "text_price" - "empty_state_text" instead of "text_no_results"
 */
@Component
@RequiredArgsConstructor
public class ResultPage {

    // v1 locators
    private static final By CONNECTION_LIST = By.id("connection_list");
    private static final By CONNECTION_ITEM = By.id("connection_item");
    private static final By ITEM_FROM = By.id("text_from");
    private static final By ITEM_TO = By.id("text_to");
    private static final By ITEM_DURATION = By.id("text_duration");
    private static final By ITEM_TRANSFERS = By.id("text_transfers");
    private static final By ITEM_PRICE = By.id("text_price");
    private static final By NO_RESULTS_MESSAGE = By.id("text_no_results");

    // v1 toolbar locators — noise-suffixed names with no alphabetic-to-position relation
    // so the broken-locator string carries no usable hint. In v2 these all collapse to
    // three identical "toolbar_action" nodes, distinguishable only by the rendered icon.
    private static final By BTN_M3N = By.id("btn_m3n");
    private static final By BTN_X7Q = By.id("btn_x7q");
    private static final By BTN_P2K = By.id("btn_p2k");
    private static final By TOOLBAR_STATUS = By.id("toolbar_status");

    private final SelfHealingAppiumDriver driver;

    public int getConnectionCount() {
        List<WebElement> items = driver.findElements(CONNECTION_ITEM);
        return items.size();
    }

    public String getFirstConnectionFrom() {
        // Use driver-level findElement so self-healing can intercept when IDs change
        return driver.findElement(ITEM_FROM).getText();
    }

    public String getFirstConnectionTo() {
        return driver.findElement(ITEM_TO).getText();
    }

    public int getFirstConnectionTransfers() {
        String text = driver.findElement(ITEM_TRANSFERS).getText();
        // Handle both v1 ("2 Umstiege") and v2 ("2x umst.")
        return Integer.parseInt(text.replaceAll("[^0-9]", ""));
    }

    public String getNoResultsMessage() {
        return driver.findElement(NO_RESULTS_MESSAGE).getText();
    }

    public void tapM3n() {
        driver.findElement(BTN_M3N).click();
    }

    public void tapX7q() {
        driver.findElement(BTN_X7Q).click();
    }

    public void tapP2k() {
        driver.findElement(BTN_P2K).click();
    }

    public String getToolbarStatus() {
        return driver.findElement(TOOLBAR_STATUS).getText();
    }
}
