# ADR-001: Appium Self-Healing Architecture

**Status:** Akzeptiert (Phasen 1–4 vollständig umgesetzt, Phase 5 teilweise — PR-Erstellung + Vision-Healing live, A2A-Modul live, iOS offen)
**Datum:** 2026-04-04 (Entscheidung) · 2026-04-20 (Status-Update)
**Entscheider:** Daniel Keiss

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
├── self-healing-a2a/                 # A2A-Protokoll-Modul (Server + Client)
├── integration-tests/                # Cucumber + Appium Tests
├── benchmark/                        # LLM-Vergleichs-Framework
├── docker/                           # Docker-Compose-Setup
├── docs/                             # Architektur- und Protokoll-Detaildokumente
├── scripts/                          # Convenience-Skripte (run-tests, verify-fix)
├── config/                           # Build-/Style-Konfiguration (Eclipse-Formatter etc.)
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

**Begründung:** Tests laufen performant mit dem nativen Appium Java Client; der MCP-Server liefert dem Healing-Agent zusätzlichen Kontext (Screenshots, DOM-Exploration) ohne eigene Server-Implementation — der offizielle `appium/appium-mcp` läuft als Sidecar-Prozess.

### 2. Self-Healing-Core: Agent-Pipeline

Die Pipeline besteht aus **4 LLM-basierten Agenten** und **2 regelbasierten Handlern**, koordiniert durch den `HealingOrchestrator`. Visualisierter Flow siehe [README — Healing-Pipeline](README.md#healing-pipeline).

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

**Spring AI Implementation:** Alle LLM-Agenten nutzen `ChatClient` mit Structured Output (`.entity(...)`); `McpContextEnricher` zusätzlich mit `ToolCallbackProvider` für die MCP-Tools. Konkrete Klassen liegen in `self-healing-core/src/main/java/de/keiss/selfhealing/core/` (`agent/`, `healing/`).

### 3. Demo-App: Versionierte Zugverbindungs-App

Backend (Spring Boot 4, REST-Endpoint `GET /api/v1/connections`) liefert statische Demo-Daten an die Android-App. Die App nutzt Jetpack Compose mit zwei Build-Flavors (`UI_VERSION=v1|v2`), die bewusst unterschiedliche Element-IDs, Layouts und Navigationsflüsse haben. Dadurch brechen v1-Tests zuverlässig bei v2 → Self-Healing wird ausgelöst.

Konkrete Locator-Änderungen v1 → v2 siehe [README — Detaillierte Locator-Änderungen](README.md#detaillierte-locator-änderungen).

### 4. Test-Architektur: Cucumber + Appium 3

Modul `integration-tests/` enthält Page Objects (`SearchPage`, `ResultPage` mit v1-Locatoren), den `SelfHealingAppiumDriver`-Decorator, Cucumber-Runner sowie Feature-Files mit deutschen Steps.

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

Modul `benchmark/` orchestriert pro Teststrecke (`tracks/easy-locator-changes.yaml`, `medium-structural.yaml`, `hard-navigation.yaml`) und LLM-Provider einen Lauf, sammelt `HealingMetrics` und aggregiert sie zu einem `BenchmarkReport`.

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

Stack aus `android-emulator` (`budtmo/docker-android`, KVM-beschleunigt), `appium-server` (Port 4723), optionalem `appium-mcp`-Sidecar, `backend` (Port 8080) und `test-runner`. Provider-Auswahl über `SPRING_PROFILES_ACTIVE` / `LLM_PROVIDER`. Aktuelle Konfiguration siehe [docker/docker-compose.yml](docker/docker-compose.yml); Setup unter Windows/WSL2 siehe [docker/PODMAN.md](docker/PODMAN.md).

---

## Options Considered

| Option | Komplexität | LLM-Kosten | Healing-Qualität | Time-to-MVP |
|---|---|---|---|---|
| **A — Reiner MCP-Ansatz** (Agent steuert alles via MCP) | Hoch | $$$ | Hoch | 6 Wochen |
| **B — Hybrid: Decorator + MCP** ← gewählt | Mittel | $ | Hoch | 4 Wochen |
| **C — Reiner Decorator** (wie AICurator, ohne MCP) | Niedrig | $ | Mittel | 2 Wochen |

**A** ist extrem teuer und langsam (jede Interaktion über LLM), keine Cucumber-Integration. **C** ist die einfachste Variante, lässt aber Screenshot- und Explorations-Kontext liegen. **B** kombiniert die schnelle Test-Ausführung von C mit dem reichen Kontext von A — Tests laufen mit nativem Appium-Client, der MCP-Kontext kommt nur bei Fehlern hinzu. Die Agent-Pipeline ermöglicht zudem Root-Cause-Analyse jenseits reinem Locator-Healing.

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
- ~~Vision-Model-Integration für Screenshot-basiertes Healing~~ — umgesetzt (`self-healing.vision.enabled`, Profil `anthropic-vision`). Offen bleibt: Vergleichs-Benchmark Vision vs. Text-only über mehrere Provider.

---

## Technologie-Stack

Aktuelle Versionen siehe [README — Technologie-Stack](README.md#technologie-stack).

---

## Action Items

- **Phase 1 (MVP):** ✅ Monorepo, Backend, App v1, Cucumber-Tests, `SelfHealingAppiumDriver`, `LocatorHealer`, Docker-Setup.
- **Phase 2 (Self-Healing Demo):** ✅ App v2, Versions-Flavor, Prompt-Templates, `StepHealer`, MCP-Sidecar + Kontext-Sammlung. Hinweis: `appium-mcp` kann laufende Session nicht sharen → Default `self-healing.mcp.enabled=false`, siehe [docs/mcp-comparison-report.md](docs/mcp-comparison-report.md).
- **Phase 3 (Root-Cause-Analyse):** ✅ `TriageAgent`, `EnvironmentChecker`, `AppBugReporter`, Drei-Stufen-Pipeline.
- **Phase 4 (LLM-Benchmark):** ✅ Benchmark-Runner, Multi-Provider (Claude, GPT, Mistral, lokal Qwen3-30B/Devstral/GLM-4.7-Flash), Metriken-Sammlung, Vergleichsbericht.
- **Phase 5 (Erweiterungen):**
  - ✅ PR-Erstellung für geheilte Locatoren (`AutoFixPrCreator` + JGit + kohsuke/github-api, Dry-Run via `SELF_HEALING_GIT_PR_DRY_RUN`).
  - ✅ Vision-Healing (`self-healing.vision.enabled`, Profil `anthropic-vision`).
  - ✅ A2A-Integration (Modul `self-healing-a2a`, siehe [docs/a2a-phase5-protocol.md](docs/a2a-phase5-protocol.md)).
  - ⬜ iOS-App-Modul.
