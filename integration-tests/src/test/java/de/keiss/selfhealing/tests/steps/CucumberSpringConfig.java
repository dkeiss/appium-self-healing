package de.keiss.selfhealing.tests.steps;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(properties = {"spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.model.mistralai.autoconfigure.MistralAiChatAutoConfiguration,"
        + "org.springframework.ai.model.mistralai.autoconfigure.MistralAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.mistralai.autoconfigure.MistralAiModerationAutoConfiguration,"
        + "org.springframework.ai.model.mistralai.autoconfigure.MistralAiOcrAutoConfiguration"})
public class CucumberSpringConfig {

    @SpringBootApplication(scanBasePackages = {"de.keiss.selfhealing.core", "de.keiss.selfhealing.tests"})
    static class TestApplication {
    }
}
