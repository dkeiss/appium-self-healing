# ADR-001: Appium Self-Healing Architecture

**Status:** Proposed
**Date:** 2026-04-04
**Deciders:** Daniel Keiss

## Context

Wir wollen ein Projekt aufbauen, das **Self-Healing für Appium-basierte Mobile-Tests** demonstriert. Wenn sich die Oberfläche einer App ändert (z.B. neue UI-Version v2), sollen fehlschlagende Tests zur Laufzeit automatisch repariert werden -- durch KI-Agenten, die mit verschiedenen LLMs arbeiten.

### Anforderungen

1. **Demo-App**: Android-App (später iOS) für Zugverbindungen (Start → Ziel)
2. **Backend**: Spring Boot 4 / Java REST-API für Verbindungsdaten
3. **Tests**: Cucumber + Appium 3, Build mit Gradle
4. **Self-Healing**: Agent repariert Tests bei UI-Änderungen zur Laufzeit
5. **Root-Cause-Analyse**: Agent ermittelt ob Fehler im Test, der Umgebung oder der App liegt
6. **Multi-LLM**: Spring AI mit austauschbaren LLM-Providern (Claude, GPT, Mistral, lokal)
7. **Docker**: Gesamter Testlauf lokal reproduzierbar in Docker
8. **LLM-Vergleich**: Teststrecken zum Benchmarking verschiedener LLMs
9. **Referenz**: Orientierung an [AICurator](https://github.com/dkeiss/aicurator) (Selenium-basiert)

### Rahmenbedingungen

- AICurator nutzt Selenium + Spring AI mit Decorator-Pattern auf dem WebDriver
- Es existiert ein offizieller [appium/appium-mcp](https://github.com/appium/appium-mcp) Server (TypeScript, 45+ Tools)
- Spring AI 2.0 bietet MCP-Client-Support, Tool Calling, und Agentic Patterns (Orchestrator-Workers, Evaluator-Optimizer)
- Kein Java-basierter Appium-MCP-Server vorhanden
- Android-Emulatoren laufen in Docker via [budtmo/docker-android](https://github.com/budtmo/docker-android)

---

## Decision

### Gesamtarchitektur: Multi-Module Gradle Monorepo mit Agent-Pipeline

```
appium-self-healing/
├── backend/                          # Spring Boot 4 REST-API (Zugverbindungen)
├── android-app/                      # Android-App (Jetpack Compose, versioniert)
├── self-healing-core/                # Wiederverwendbare Self-Healing-Bibliothek
├── integration-tests/                # Cucumber + Appium Tests
├── benchmark/                        # LLM-Vergleichs-Framework
├── docker/                           # Docker-Compose-Setup
├── settings.gradle.kts
└── build.gradle.kts
```

---

## Architektur-Entscheidungen im Detail

### 1. Appium-Integration: Hybrid-Ansatz (MCP + Direct Driver)

| Dimension | Assessment |
|-----------|------------|
| Komplexität | Mittel |
| Flexibilität | Hoch |
| Wartbarkeit | Hoch |
| Team-Fit | Hoch (Java/Spring-Stack) |

**Entscheidung:** Wir nutzen einen **zweistufigen Ansatz**:

- **Teststufe (Integration-Tests):** Appium Java Client direkt, eingewickelt in einen `SelfHealingAppiumDriver` (Decorator-Pattern wie AICurator)
- **Agent-Stufe (Self-Healing-Core):** Spring AI MCP Client verbindet sich zum offiziellen `appium/appium-mcp` Server. Der Healing-Agent nutzt MCP-Tools (Screenshot, Page Source, Element-Suche) für kontextreiche Analyse.

```
┌─────────────────────────────────────────────────────────────────┐
│                     Integration Tests                           │
│  Cucumber Steps → Page Objects → SelfHealingAppiumDriver        │
│                                      │                          │
│                              findElement() schlägt fehl         │
│                                      │                          │
│                                      ▼                          │
│                            ┌─────────────────┐                  │
│                            │  Healing Agent   │                  │
│                            │  (Spring AI)     │                  │
│                            └────────┬────────┘                  │
│                                     │                           │
│                    ┌────────────────┼────────────────┐          │
│                    ▼                ▼                ▼          │
│              Page Source      Screenshot       Test-Code        │
│              (Appium)        (Appium MCP)    (Filesystem)       │
│                    │                │                │          │
│                    └────────────────┼────────────────┘          │
│                                     ▼                           │
│                               LLM Analyse                       │
│                          (Claude/GPT/Mistral)                   │
│                                     │                           │
│                                     ▼                           │
│                          Geheilter Locator                      │
│                          + Code-Fix-Vorschlag                   │
└─────────────────────────────────────────────────────────────────┘
```

**Begründung:**
- Tests laufen performant mit dem nativen Appium Java Client
- Der MCP-Server wird vom Healing-Agent genutzt, um reichhaltigen Kontext zu sammeln (Screenshots, DOM-Exploration)
- Kein eigener MCP-Server nötig -- der offizielle `appium/appium-mcp` läuft als Sidecar-Prozess

### 2. Self-Healing-Core: Agent-Pipeline

Die Pipeline besteht aus **4 LLM-basierten Agenten** und **2 regelbasierten Handlern**, koordiniert durch den `HealingOrchestrator`:

```
Test schlägt fehl
        │
        ▼
┌──────────────────┐
│  PromptCache     │  Cache-Hit? → Sofort wiederverwenden
└────────┬─────────┘
         │ MISS
         ▼
┌──────────────────┐
│ McpContext-       │  ← Stufe 0 (optional): Kontext-Anreicherung
│ Enricher (LLM)   │     Screenshot + DOM via Appium MCP
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Triage Agent    │  ← Stufe 1: Root-Cause-Analyse (LLM)
│  (Klassifikation)│
└────────┬─────────┘
         │
    ┌────┼────────┬───────────────┐
    ▼    ▼        ▼               ▼
┌──────┐ ┌────┐ ┌──────────┐ ┌──────────┐
│LOCATOR│ │STEP│ │ENVIRONMENT│ │ APP BUG  │  ← Stufe 2: Handler
│HEALER │ │HEAL│ │ CHECKER   │ │ REPORTER │
│ (LLM) │ │(LLM)│ │(regelbasiert)│ │(regelbasiert)│
└───┬───┘ └──┬─┘ └─────┬────┘ └─────┬────┘
    │        │          │             │
    ▼        ▼          ▼             ▼
  Reparatur Code-     Diagnose     Bug-Report
  zur       Vorschlag + Retry      (kein Heal)
  Laufzeit
    │
    ▼
┌──────────────────┐
│ Stufe 3:         │  Cache + HealingEvent publizieren
│ Post-Processing  │
└──────────────────┘
```

**LLM-basierte Agenten (4):**

| Agent | Stufe | Aufgabe | LLM-Call |
|-------|-------|---------|----------|
| `McpContextEnricher` | 0 (optional) | Reichert Kontext via MCP-Tools an (Screenshot, DOM, Element-Exploration) | `ChatClient` + `ToolCallbackProvider` (MCP) |
| `TriageAgent` | 1 | Klassifiziert Fehler in: `LOCATOR_CHANGED`, `TEST_LOGIC_ERROR`, `ENVIRONMENT_ISSUE`, `APP_BUG` | `ChatClient` → Structured Output |
| `LocatorHealer` | 2a | Findet alternativen Locator via LLM + DOM-Analyse | `ChatClient` → Structured Output |
| `StepHealer` | 2b | Repariert Step-Logik (z.B. geänderter Flow) | `ChatClient` → Structured Output |

**Regelbasierte Handler (2, kein LLM):**

| Handler | Stufe | Aufgabe | Mechanismus |
|---------|-------|---------|-------------|
| `EnvironmentChecker` | 2c | Prüft Server-Erreichbarkeit, Emulator-Status, Page-Source-Heuristik | HTTP Health Checks (`HttpClient`) |
| `AppBugReporter` | 2d | Dokumentiert Bug mit Screenshot, Kontext und Reproduktionsschritten als JSON | Regelbasierte Report-Erstellung + Event-Publishing |

**Stufe 3 -- Post-Processing (im `HealingOrchestrator`):**
- Bei Erfolg: `PromptCache.put()` + `HealingEvent` publizieren
- Bei Misserfolg: Eskaliert (max. N Versuche konfigurierbar)
- Hinweis: Ein separater Verification Agent ist geplant, aktuell übernimmt der `SelfHealingAppiumDriver` die Retry-Logik

**Spring AI Implementation:**

```java
// LLM-Agent: TriageAgent -- nutzt Spring AI ChatClient mit Structured Output
public class TriageAgent {
    private final ChatClient chatClient;

    public TriageResult analyze(FailureContext context) {
        return chatClient.prompt()
            .system(triageSystemPrompt)
            .user(buildUserPrompt(context))
            .call()
            .entity(TriageResult.class);
    }
}

// LLM-Agent: McpContextEnricher -- nutzt MCP-Tools für Kontext-Sammlung
public class McpContextEnricher {
    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpToolProvider; // Appium MCP

    public FailureContext enrich(FailureContext context) {
        chatClient.prompt()
            .system(enrichmentSystemPrompt)
            .user(buildEnrichmentPrompt(context))
            .tools(mcpToolProvider)
            .call()
            .content();
        // ...
    }
}

// Regelbasiert: EnvironmentChecker -- HTTP Health Checks, kein LLM
public class EnvironmentChecker {
    private final HttpClient httpClient;

    public EnvironmentReport check(FailureContext context) {
        // HTTP probes gegen Backend, Appium Server
        // Page-Source-Heuristik (leer = Emulator-Problem)
    }
}

// Regelbasiert: AppBugReporter -- Strukturierter Report, kein LLM
public class AppBugReporter {
    public BugReport report(FailureContext context, TriageResult triage) {
        // Severity-Ableitung aus Triage-Confidence
        // JSON-Persistierung + Screenshot + Event-Publishing
    }
}
```

### 3. Demo-App: Versionierte Zugverbindungs-App

**Backend (Spring Boot 4):**

```
backend/
├── src/main/java/.../
│   ├── BackendApplication.java
│   ├── controller/
│   │   └── ConnectionController.java    # GET /api/v1/connections?from=X&to=Y
│   ├── model/
│   │   └── Connection.java              # Abfahrt, Ankunft, Umsteigen, Preis
│   └── service/
│       └── ConnectionService.java       # Statische Demo-Daten
```

**Android-App (Jetpack Compose, versioniert):**

```
android-app/
├── src/main/java/.../
│   ├── MainActivity.java
│   ├── ui/
│   │   ├── v1/                          # Version 1: Standard-Layout
│   │   │   ├── SearchScreen.java        # Einfache Suche
│   │   │   └── ResultScreen.java        # Liste
│   │   └── v2/                          # Version 2: Geändertes Layout
│   │       ├── SearchScreen.java        # Redesigned (andere IDs, Struktur)
│   │       └── ResultScreen.java        # Cards statt Liste
│   └── config/
│       └── AppConfig.java               # UI_VERSION=v1|v2 (Build-Config)
```

**Versionierung:**
- Die App-Version wird über eine Build-Variable (`UI_VERSION`) gesteuert
- v1 und v2 haben **bewusst unterschiedliche** Element-IDs, Layouts und Navigationsflüsse
- Dadurch brechen v1-Tests zuverlässig bei v2 → Self-Healing wird ausgelöst

**Geplante UI-Änderungen v1 → v2:**

| Element | v1 | v2 | Heal-Schwierigkeit |
|---------|----|----|-------------------|
| Startbahnhof-Input | `@+id/input_from` | `@+id/departure_station` | Einfach (ID-Rename) |
| Suchbutton | `@+id/btn_search` | `@+id/fab_search` (FAB) | Mittel (Typ-Änderung) |
| Ergebnisliste | `RecyclerView` | `LazyColumn` (Compose) | Schwer (Struktur) |
| Detailansicht | Neues Activity | BottomSheet | Schwer (Navigation) |

### 4. Test-Architektur: Cucumber + Appium 3

```
integration-tests/
├── src/test/java/.../
│   ├── runner/
│   │   └── TestRunner.java              # Cucumber Runner
│   ├── steps/
│   │   ├── ConnectionSearchSteps.java   # Schritte für Verbindungssuche
│   │   └── SelfHealingSteps.java        # Self-Healing Konfiguration
│   ├── pages/
│   │   ├── SearchPage.java              # Page Object: Suche
│   │   └── ResultPage.java              # Page Object: Ergebnis
│   ├── driver/
│   │   └── SelfHealingAppiumDriver.java # Decorator um AppiumDriver
│   └── config/
│       └── TestConfig.java              # Spring-Konfiguration
├── src/test/resources/
│   └── features/
│       ├── connection_search.feature
│       └── self_healing.feature
└── build.gradle.kts
```

**Beispiel-Feature:**

```gherkin
Feature: Zugverbindung suchen

  Background:
    Given die App ist gestartet
    And Self-Healing ist aktiviert mit Provider "claude"

  Scenario: Direkte Verbindung finden
    When ich "Berlin Hbf" als Startbahnhof eingebe
    And ich "München Hbf" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich mindestens 1 Verbindung
    And die erste Verbindung zeigt "Berlin Hbf" nach "München Hbf"

  Scenario: Verbindung mit Umstieg
    When ich "Hamburg Hbf" als Startbahnhof eingebe
    And ich "Stuttgart Hbf" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich mindestens 1 Verbindung mit Umstieg
```

### 5. LLM-Benchmark-Framework

```
benchmark/
├── src/main/java/.../
│   ├── BenchmarkRunner.java             # Orchestriert Teststrecken
│   ├── model/
│   │   ├── BenchmarkRun.java            # Einzelner Lauf
│   │   ├── HealingMetrics.java          # Metriken pro Healing-Versuch
│   │   └── BenchmarkReport.java         # Aggregierter Bericht
│   ├── provider/
│   │   ├── LlmProviderConfig.java       # Multi-Provider Setup
│   │   └── TestTrack.java               # Definition einer Teststrecke
│   └── report/
│       └── ComparisonReportGenerator.java
├── src/main/resources/
│   └── tracks/
│       ├── easy-locator-changes.yaml    # Einfache ID-Änderungen
│       ├── medium-structural.yaml       # Struktur-Änderungen
│       └── hard-navigation.yaml         # Navigations-Änderungen
└── build.gradle.kts
```

**Teststrecken-Definition (YAML):**

```yaml
# tracks/easy-locator-changes.yaml
name: "Einfache Locator-Änderungen"
description: "Tests mit umbenannten Element-IDs"
appVersion: "v2"
scenarios:
  - feature: "connection_search.feature"
    scenario: "Direkte Verbindung finden"
    expectedHealings: 3
    maxHealingTimeMs: 10000

metrics:
  - healing_success_rate
  - avg_healing_time_ms
  - total_llm_tokens
  - total_llm_cost_usd
  - false_positive_rate     # Fälschlich als geheilt markiert
```

**Benchmark-Ablauf:**

```
für jeden LLM-Provider:
  für jede Teststrecke:
    1. App mit Ziel-Version starten
    2. Test-Suite ausführen (mit Self-Healing aktiv)
    3. Metriken sammeln (Erfolg, Zeit, Tokens, Kosten)
    4. Ergebnis persistieren

Vergleichsbericht generieren (Tabelle + optional HTML)
```

### 6. Docker-Setup

```yaml
# docker/docker-compose.yml
services:
  android-emulator:
    image: budtmo/docker-android:emulator_14.0
    ports:
      - "6080:6080"    # noVNC (Browser-Zugriff auf Emulator)
      - "5555:5555"    # ADB
    environment:
      - EMULATOR_DEVICE=pixel_6
      - WEB_VNC=true
    devices:
      - /dev/kvm       # KVM-Beschleunigung (Linux)

  appium-server:
    image: appium/appium:latest
    ports:
      - "4723:4723"
    depends_on:
      - android-emulator
    environment:
      - ANDROID_DEVICE_HOST=android-emulator

  appium-mcp:
    image: node:22-slim
    command: npx appium-mcp@latest
    environment:
      - APPIUM_HOST=appium-server
      - APPIUM_PORT=4723

  backend:
    build: ../backend
    ports:
      - "8080:8080"

  self-healing-runner:
    build: ../integration-tests
    depends_on:
      - appium-server
      - appium-mcp
      - backend
    environment:
      - APPIUM_URL=http://appium-server:4723
      - BACKEND_URL=http://backend:8080
      - MCP_SERVER_URL=appium-mcp
      - SPRING_AI_PROVIDER=claude  # oder: chatgpt, mistral, local
    volumes:
      - ./results:/app/results
```

**Ausführung:**

```bash
# Einfacher Testlauf
docker compose up --build

# Benchmark mit verschiedenen LLMs
docker compose run self-healing-runner --profile=claude
docker compose run self-healing-runner --profile=chatgpt
docker compose run self-healing-runner --profile=mistral

# Vergleichsbericht
docker compose run self-healing-runner --benchmark --report
```

---

## Options Considered

### Option A: Reiner MCP-Ansatz (Agent steuert alles über MCP)

| Dimension | Assessment |
|-----------|------------|
| Komplexität | Hoch |
| Performance | Niedrig (jede Interaktion über LLM) |
| Flexibilität | Sehr hoch |
| Kosten (LLM-Tokens) | Sehr hoch |

**Pros:** Maximale Flexibilität, Agent kann frei explorieren, kein Test-Code nötig
**Cons:** Extrem langsam, teuer, nicht reproduzierbar, kein Cucumber-Integration

### Option B: Hybrid-Ansatz (Decorator + MCP für Healing) ← **Gewählt**

| Dimension | Assessment |
|-----------|------------|
| Komplexität | Mittel |
| Performance | Hoch (LLM nur bei Fehler) |
| Flexibilität | Hoch |
| Kosten (LLM-Tokens) | Niedrig-Mittel |

**Pros:** Schnelle Tests im Normalfall, LLM nur bei Bedarf, volle Cucumber-Integration, MCP für reichhaltigen Kontext
**Cons:** Erfordert Decorator-Implementation, zwei Integrationspfade

### Option C: Reiner Decorator-Ansatz (wie AICurator, ohne MCP)

| Dimension | Assessment |
|-----------|------------|
| Komplexität | Niedrig |
| Performance | Hoch |
| Flexibilität | Mittel |
| Kosten (LLM-Tokens) | Niedrig |

**Pros:** Einfachste Implementation, bewährt (AICurator), kein MCP-Setup
**Cons:** Kein Screenshot-/Explorations-Kontext für den Agent, weniger intelligentes Healing

---

## Trade-off Analysis

| Kriterium | Option A (MCP-only) | Option B (Hybrid) | Option C (Decorator-only) |
|-----------|---------------------|-------------------|--------------------------|
| Time-to-MVP | 6 Wochen | 4 Wochen | 2 Wochen |
| Healing-Qualität | Hoch | Hoch | Mittel |
| Testlauf-Dauer | Minuten/Test | Sekunden + Heal-Time | Sekunden + Heal-Time |
| LLM-Kosten/Lauf | $$$ | $ | $ |
| Erweiterbarkeit | Gut | Sehr gut | Eingeschränkt |
| Root-Cause-Analyse | Natürlich | Gut integrierbar | Schwierig |

**Option B** bietet den besten Kompromiss: Tests laufen schnell mit nativem Appium-Client, aber bei Fehlern steht dem Healing-Agent der volle MCP-Kontext (Screenshot, DOM-Exploration) zur Verfügung. Die Drei-Stufen-Agent-Pipeline ermöglicht später die Root-Cause-Analyse.

---

## Consequences

### Was einfacher wird
- Self-Healing-Demos mit verschiedenen UI-Versionen reproduzierbar ausführen
- LLM-Provider wechseln durch Spring-Profile-Änderung
- Benchmark-Vergleiche verschiedener LLMs automatisiert durchführen
- Spätere Erweiterung um iOS durch zusätzliches App-Modul

### Was schwieriger wird
- Docker-Setup für Android-Emulator erfordert KVM (Linux) oder alternative Lösung (Windows/Mac)
- Zwei Integrationspfade (Direct Driver + MCP) müssen synchron gehalten werden
- Appium MCP Server ist TypeScript -- Abhängigkeit von Node.js im Stack

### Was später revisited werden muss
- iOS-App-Modul und XCUITest-Integration
- Persistierung geheilter Locatoren über Runtime-Fix hinaus: PR-Erstellung ist umgesetzt (`AutoFixPrCreator` + `GitService` + `GitHubPrService`, inkl. Dry-Run-Modus). Offen ist eine zusätzliche In-Repo-Cache-Persistenz für wiederkehrende Heilungen.
- A2A-Protocol-Integration für Agent-zu-Agent-Kommunikation (Spring AI A2A)
- Vision-Model-Integration für Screenshot-basiertes Healing (Appium MCP unterstützt bereits Qwen3-VL)

---

## Technologie-Stack Zusammenfassung

| Komponente | Technologie |
|-----------|------------|
| Sprache | Java 25 |
| Build | Gradle 9.4.1 (Kotlin DSL) |
| Backend | Spring Boot 4.0.5 |
| Android-App | Jetpack Compose, versioniert (v1/v2) |
| Test-Framework | Cucumber 7.34 + Appium Java Client 10 |
| AI-Framework | Spring AI 2.0 |
| MCP-Server | appium/appium-mcp (offiziell, TypeScript) |
| MCP-Client | spring-ai-starter-mcp-client |
| LLM-Provider | Claude, GPT, Mistral, LM Studio (lokal) |
| Container | Docker Compose |
| Android-Emulator | budtmo/docker-android |
| CI/CD | GitHub Actions (später) |

---

## Action Items

### Phase 1: Foundation (MVP)
1. [ ] Gradle-Monorepo mit allen Modulen aufsetzen
2. [ ] Spring Boot 4 Backend mit Zugverbindungs-API implementieren
3. [ ] Android-App v1 mit Jetpack Compose erstellen (Suche + Ergebnis)
4. [ ] Cucumber-Tests mit Appium und Page Objects schreiben
5. [ ] `SelfHealingAppiumDriver` (Decorator) implementieren
6. [ ] `LocatorHealer` mit Spring AI ChatClient implementieren
7. [ ] Docker-Compose-Setup für lokale Ausführung erstellen

### Phase 2: Self-Healing Demo
8. [ ] Android-App v2 mit geänderten IDs/Layouts erstellen
9. [ ] Versions-Umschaltung (Build-Variable) implementieren
10. [ ] Prompt-Templates für Locator-Healing optimieren
11. [ ] `StepHealer` für Step-Level-Reparatur implementieren
12. [ ] Appium MCP Server als Sidecar integrieren
13. [ ] MCP-basierte Kontext-Sammlung (Screenshot, DOM) einbauen

### Phase 3: Root-Cause-Analyse
14. [ ] `TriageAgent` mit Fehler-Klassifikation implementieren
15. [ ] `EnvironmentChecker` für Infrastruktur-Probleme implementieren
16. [ ] `AppBugReporter` für Bug-Dokumentation implementieren
17. [ ] Drei-Stufen-Pipeline (Triage → Handler → Verification) verdrahten

### Phase 4: LLM-Benchmark
18. [ ] Benchmark-Runner mit Teststrecken-YAML implementieren
19. [ ] Multi-Provider-Konfiguration (Claude, GPT, Mistral, lokal) einrichten
20. [ ] Metriken-Sammlung (Erfolgsrate, Zeit, Tokens, Kosten) implementieren
21. [ ] Vergleichsbericht-Generator erstellen

### Phase 5: Erweiterungen
22. [ ] iOS-App-Modul hinzufügen
23. [x] PR-Erstellung für geheilte Locatoren (wie AICurator) — `AutoFixPrCreator` + JGit + kohsuke/github-api, Dry-Run-Modus via `SELF_HEALING_GIT_PR_DRY_RUN`, Submodul-aware Pfad-Resolver, verifiziert mit Anthropic Sonnet und lokalem Devstral. Siehe README-Abschnitt "PR-Erstellung für geheilte Locatoren".
24. [ ] Vision-Model-basiertes Healing (Screenshot-Analyse)
25. [ ] A2A-Integration für Multi-Agent-Kommunikation
