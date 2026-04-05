package de.keiss.selfhealing.tests.steps;

import de.keiss.selfhealing.tests.pages.ResultPage;
import de.keiss.selfhealing.tests.pages.SearchPage;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Und;
import io.cucumber.java.de.Wenn;
import lombok.RequiredArgsConstructor;

import static org.junit.jupiter.api.Assertions.*;

@RequiredArgsConstructor
public class ConnectionSearchSteps {

    private final SearchPage searchPage;
    private final ResultPage resultPage;

    @Wenn("ich {string} als Startbahnhof eingebe")
    public void enterDeparture(String station) {
        searchPage.enterDeparture(station);
    }

    @Und("ich {string} als Zielbahnhof eingebe")
    public void enterDestination(String station) {
        searchPage.enterDestination(station);
    }

    @Und("ich auf Suchen klicke")
    public void clickSearch() {
        searchPage.clickSearch();
    }

    @Dann("sehe ich mindestens {int} Verbindung")
    public void seeAtLeastNConnections(int minCount) {
        int count = resultPage.getConnectionCount();
        assertTrue(count >= minCount, "Expected at least " + minCount + " connection(s), but found " + count);
    }

    @Und("die erste Verbindung zeigt {string} als Start")
    public void firstConnectionShowsDeparture(String expected) {
        assertEquals(expected, resultPage.getFirstConnectionFrom());
    }

    @Und("die erste Verbindung zeigt {string} als Ziel")
    public void firstConnectionShowsDestination(String expected) {
        assertEquals(expected, resultPage.getFirstConnectionTo());
    }

    @Und("die erste Verbindung hat mindestens {int} Umstieg")
    public void firstConnectionHasTransfers(int minTransfers) {
        int transfers = resultPage.getFirstConnectionTransfers();
        assertTrue(transfers >= minTransfers,
                "Expected at least " + minTransfers + " transfer(s), but found " + transfers);
    }

    @Dann("sehe ich eine Meldung {string}")
    public void seeMessage(String expected) {
        String actual = resultPage.getNoResultsMessage();
        assertTrue(actual.contains(expected),
                "Expected message containing '" + expected + "', but got '" + actual + "'");
    }
}
