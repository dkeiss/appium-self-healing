package de.keiss.selfhealing.tests.steps;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest
public class CucumberSpringConfig {

    @SpringBootApplication(scanBasePackages = {"de.keiss.selfhealing.core", "de.keiss.selfhealing.tests"})
    static class TestApplication {
    }
}
