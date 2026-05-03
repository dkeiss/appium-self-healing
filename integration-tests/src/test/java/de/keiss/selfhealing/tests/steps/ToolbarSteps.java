package de.keiss.selfhealing.tests.steps;

import de.keiss.selfhealing.tests.pages.ResultPage;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Wenn;
import lombok.RequiredArgsConstructor;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiredArgsConstructor
public class ToolbarSteps {

    private final ResultPage resultPage;

    @Wenn("ich auf Aktion A klicke")
    public void clickActionA() {
        resultPage.tapActionA();
    }

    @Wenn("ich auf Aktion B klicke")
    public void clickActionB() {
        resultPage.tapActionB();
    }

    @Wenn("ich auf Aktion C klicke")
    public void clickActionC() {
        resultPage.tapActionC();
    }

    @Dann("zeigt der Toolbar-Status {string}")
    public void toolbarStatusEquals(String expected) {
        assertEquals(expected, resultPage.getToolbarStatus());
    }
}
