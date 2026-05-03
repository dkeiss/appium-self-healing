package de.keiss.selfhealing.tests.steps;

import de.keiss.selfhealing.tests.pages.ResultPage;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Wenn;
import lombok.RequiredArgsConstructor;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiredArgsConstructor
public class ToolbarSteps {

    private final ResultPage resultPage;

    @Wenn("ich auf den Button m3n klicke")
    public void clickM3n() {
        resultPage.tapM3n();
    }

    @Wenn("ich auf den Button x7q klicke")
    public void clickX7q() {
        resultPage.tapX7q();
    }

    @Wenn("ich auf den Button p2k klicke")
    public void clickP2k() {
        resultPage.tapP2k();
    }

    @Dann("zeigt der Toolbar-Status {string}")
    public void toolbarStatusEquals(String expected) {
        assertEquals(expected, resultPage.getToolbarStatus());
    }
}
