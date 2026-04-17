package de.keiss.selfhealing.tests.steps;

import de.keiss.selfhealing.tests.pages.DetailPage;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Wenn;
import lombok.RequiredArgsConstructor;

import static org.junit.jupiter.api.Assertions.assertFalse;

@RequiredArgsConstructor
public class VeryHardNavigationSteps {

    private final DetailPage detailPage;

    @Wenn("ich auf die erste Verbindung tippe")
    public void tapFirstConnection() {
        detailPage.tapFirstConnection();
    }

    @Dann("sehe ich die Zugnummer des ersten Abschnitts")
    public void seeTrainNumberOfFirstLeg() {
        String trainNumber = detailPage.getFirstLegTrainNumber();
        assertFalse(trainNumber == null || trainNumber.isBlank(),
                "Expected a non-empty train number for the first leg, got: '" + trainNumber + "'");
    }

    @Dann("sehe ich das Gleis des ersten Abschnitts")
    public void seePlatformOfFirstLeg() {
        String platform = detailPage.getFirstLegPlatform();
        assertFalse(platform == null || platform.isBlank(),
                "Expected a non-empty platform for the first leg, got: '" + platform + "'");
    }
}
