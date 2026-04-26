package de.keiss.selfhealing.core.healing;

import org.openqa.selenium.By;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Reconstructs a Selenium/Appium {@link By} from a method name plus value. Used by both the in-process healer and the
 * A2A client adapter so that the locator is built identically on either side of the wire.
 */
public final class LocatorFactory {

    private static final String APPIUM_BY_CLASS = "io.appium.java_client.AppiumBy";

    private LocatorFactory() {
    }

    public static By construct(String method, String value) throws ReflectiveOperationException {
        Objects.requireNonNull(value, "locator value cannot be null");
        return switch (method.toLowerCase()) {
            case "id" -> By.id(stripResourceIdPrefix(value));
            case "xpath" -> By.xpath(value);
            case "cssselector", "css" -> By.cssSelector(value);
            case "classname" -> By.className(value);
            case "name" -> By.name(value);
            case "accessibilityid" -> {
                Class<?> appiumBy = Class.forName(APPIUM_BY_CLASS);
                Method m = appiumBy.getMethod("accessibilityId", String.class);
                yield (By) m.invoke(null, value);
            }
            case "androiduiautomator" -> {
                Class<?> appiumBy = Class.forName(APPIUM_BY_CLASS);
                Method m = appiumBy.getMethod("androidUIAutomator", String.class);
                yield (By) m.invoke(null, value);
            }
            default -> {
                try {
                    Class<?> appiumBy = Class.forName(APPIUM_BY_CLASS);
                    Method m = appiumBy.getMethod(method, String.class);
                    yield (By) m.invoke(null, value);
                } catch (Exception _) {
                    // AppiumBy doesn't know this method — fall back to standard Selenium By
                    Method m = By.class.getMethod(method, String.class);
                    yield (By) m.invoke(null, value);
                }
            }
        };
    }

    /**
     * Compose-backed resource-ids are often exposed without the package prefix. Some LLMs return the full
     * {@code pkg:id/name} form, which breaks on devices where {@code disableIdLocatorAutocompletion} is set. Strip the
     * prefix so both styles work.
     */
    private static String stripResourceIdPrefix(String value) {
        if (value.contains(":id/")) {
            return value.substring(value.indexOf(":id/") + 4);
        }
        return value;
    }
}
