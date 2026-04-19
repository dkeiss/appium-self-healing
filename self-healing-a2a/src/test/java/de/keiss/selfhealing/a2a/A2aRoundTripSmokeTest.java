package de.keiss.selfhealing.a2a;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import de.keiss.selfhealing.a2a.client.A2AClient;
import de.keiss.selfhealing.a2a.client.A2ALocatorHealer;
import de.keiss.selfhealing.core.healing.ChatClientLocatorHealer;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end roundtrip test for the A2A module. Starts the server autoconfig against a stubbed
 * {@link ChatClientLocatorHealer} on a random port, builds an {@link A2AClient} pointing at the local URL, and asserts
 * that a {@link FailureContext} survives serialization, JSON-RPC transport, and DTO mapping — returning a healed
 * {@link HealingResult} whose {@code By} is reconstructed identically on both sides. No live LLM is involved.
 */
@SpringBootTest(classes = A2aRoundTripSmokeTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "self-healing.a2a.server.enabled=true", "self-healing.a2a.server.base-path=/a2a",
        // Spring AI starters (anthropic/openai/mistral) leak onto the test classpath via self-healing-core's
        // implementation deps. Provide stub keys so their autoconfigs don't explode; we never actually call out.
        "spring.ai.anthropic.api-key=stub", "spring.ai.openai.api-key=stub", "spring.ai.mistralai.api-key=stub",
        "spring.main.banner-mode=off"})
class A2aRoundTripSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ChatClientLocatorHealer stubHealer;

    @Test
    void clientRoundTrip_deliversFailureContextToServer_andReturnsHealedLocator() {
        var restClient = new A2AClient("http://localhost:" + port + "/a2a", objectMapper, Duration.ofSeconds(10));
        var remote = new A2ALocatorHealer(restClient);

        var originalContext = new FailureContext("io.appium.java_client.NoSuchElementException: no such element",
                "<hierarchy><node resource-id=\"search\"/></hierarchy>", new byte[0], By.id("search_button"),
                "class SearchPage { By search = By.id(\"search_button\"); }", "SearchPage",
                "Given(\"tap search\", ...)", "User taps search", "mcp-note: resource ids changed",
                List.of(By.id("old_rejected")));

        HealingResult result = remote.heal(originalContext);

        assertThat(result.success()).as("heal should succeed and round-trip through A2A").isTrue();
        assertThat(result.healedLocator()).as("By reconstructed on client side").isEqualTo(By.id("healed_button"));
        assertThat(result.healedLocatorExpression()).isEqualTo("id(\"healed_button\")");
        assertThat(result.explanation()).contains("stubbed heal for User taps search");
        assertThat(result.healingDurationMs()).isEqualTo(42L);

        FailureContext received = ((RecordingStubHealer) stubHealer).lastContext.get();
        assertThat(received).as("server-side healer received a context").isNotNull();
        assertThat(received.exceptionMessage()).isEqualTo(originalContext.exceptionMessage());
        assertThat(received.pageSourceXml()).isEqualTo(originalContext.pageSourceXml());
        assertThat(received.pageObjectClassName()).isEqualTo("SearchPage");
        assertThat(received.stepName()).isEqualTo("User taps search");
        assertThat(received.additionalContext()).isEqualTo("mcp-note: resource ids changed");
        assertThat(received.failedLocator().toString()).as("failed locator survives via StringBy wrapper")
                .isEqualTo(originalContext.failedLocator().toString());
        assertThat(received.rejectedLocators()).hasSize(1);
        assertThat(received.rejectedLocators().get(0).toString()).isEqualTo("By.id: old_rejected");
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        public ChatClientLocatorHealer chatClientLocatorHealer() {
            return new RecordingStubHealer();
        }

        @Bean
        public ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    /**
     * Stub subclass that skips the real ChatClient call — the smoke test only verifies transport & mapping, not LLM
     * behaviour. Also records the incoming context so the test can assert it was deserialized correctly.
     */
    static class RecordingStubHealer extends ChatClientLocatorHealer {

        final AtomicReference<FailureContext> lastContext = new AtomicReference<>();

        RecordingStubHealer() {
            super(null, null);
        }

        @Override
        public HealingResult heal(FailureContext context) {
            lastContext.set(context);
            return new HealingResult(true, By.id("healed_button"), "id(\"healed_button\")", null,
                    "stubbed heal for " + context.stepName(), 42L, 0);
        }
    }
}
