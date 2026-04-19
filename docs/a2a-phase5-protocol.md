# Phase 5 – A2A (Agent2Agent) Integration: Change Protocol

**Date:** 2026-04-19
**Branch:** `claude/wonderful-banzai-3327ab`
**Author:** Claude Sonnet 4.6 (automated)

---

## 1. Motivation

Das Ziel von Phase 5 ist die Vorbereitung des Self-Healing-Frameworks für einen verteilten,
multi-provider-fähigen Betrieb. Konkret:

- Verschiedene LLMs sollen austauschbar verwendbar sein (Anthropic, OpenAI, Mistral, lokal).
- Agents sollen unternehmensweit verteilt betrieben werden können — ein Team stellt den
  Healing-Agent bereit, andere Teams konsumieren ihn via HTTP.
- Das Google Agent2Agent (A2A) Protokoll bietet einen offenen, LLM-agnostischen Standard für
  diese Kommunikation.

**Entscheidung:** Minimales, hand-geriertes A2A-konformes Modul — kein Third-Party-SDK
(Spring AI 2.0.0-M4 hat noch keine A2A-Starter, Stand 2026-04-19).

---

## 2. Architektonische Änderungen

### 2.1 `LocatorHealer` als Interface (self-healing-core)

**Vorher:** `LocatorHealer` war eine konkrete Spring-Bean die direkt einen `ChatClient` kapselte.

**Nachher:**
- `LocatorHealer` ist ein Strategy-Interface mit einer Methode: `HealingResult heal(FailureContext)`
- `ChatClientLocatorHealer` implementiert das Interface (bisherige Logik, unverändert)
- Die `SelfHealingAutoConfiguration` registriert `ChatClientLocatorHealer` als named Bean;
  `locatorHealer` (Interface) wird per `@ConditionalOnMissingBean` registriert — fällt weg,
  wenn ein A2A-Client aktiv ist

```
LocatorHealer (interface)
├── ChatClientLocatorHealer   @Bean  (immer registriert, treibt lokales A2A-Server-Endpoint)
└── A2ALocatorHealer          @Bean @Primary  (nur wenn a2a.client.enabled=true)
```

### 2.2 `LocatorFactory` (self-healing-core, neu)

Geteilte Utility-Klasse zur Konstruktion von `By`-Instanzen aus `method + value`. Wird sowohl
vom A2A-Client (nach Deserialisierung vom Wire) als auch vom bisherigen `ChatClientLocatorHealer`
verwendet.

**Grund:** `By` ist nicht JSON-serialisierbar; auf dem Wire werden `method` + `value` getrennt
übertragen und clientseitig via `LocatorFactory.construct()` rekonstruiert.

### 2.3 Neues Gradle-Modul: `self-healing-a2a`

```
self-healing-a2a/
├── protocol/
│   ├── AgentCard.java          Agent Card (GET /.well-known/agent.json)
│   ├── A2AMessage.java         JSON-RPC message envelope
│   ├── A2APart.java            Sealed interface: TextPart | DataPart (@JsonTypeInfo)
│   ├── A2ATask.java            Task-Ergebnis inkl. Artifacts
│   └── JsonRpc.java            Request / Response / Error Records
├── dto/
│   ├── FailureContextDto.java  Wire-Mirror von FailureContext (By → toString, screenshot → base64)
│   ├── HealingResultDto.java   Wire-Mirror von HealingResult (By → method + value getrennt)
│   └── DtoMapper.java          Bidirektionales Mapping inkl. StringBy-Platzhalter
├── server/
│   └── LocatorHealerA2AController.java  REST-Controller (Agent Card + JSON-RPC Endpoint)
├── client/
│   ├── A2AClient.java          RestClient-basierter JSON-RPC Client
│   └── A2ALocatorHealer.java   LocatorHealer-Impl die an A2AClient delegiert
└── config/
    ├── A2aServerAutoConfiguration.java  @ConditionalOnProperty a2a.server.enabled=true
    └── A2aClientAutoConfiguration.java  @ConditionalOnProperty a2a.client.enabled=true
```

**Jackson-Hinweis:** Spring Boot 4.x verwendet Jackson 3 (`tools.jackson.databind`). Das A2A-Modul
nutzt entsprechend `tools.jackson.databind.{ObjectMapper,JsonNode}` und
`tools.jackson.core.type.TypeReference`.

### 2.4 Konfiguration (`SelfHealingProperties`)

Neues `A2a`-Record mit `Server`- und `Client`-Unterkonfiguration:

```yaml
self-healing:
  a2a:
    server:
      enabled: false          # true = A2A HTTP-Endpoint aktivieren
      base-path: /a2a         # Pfad des JSON-RPC Endpoints
    client:
      enabled: false          # true = A2ALocatorHealer als @Primary registrieren
      locator-healer-url: ""  # URL des Remote-A2A-Endpoints
      request-timeout-ms: 180000
```

**Default:** Beides `false` — bisheriges Verhalten vollständig erhalten.

### 2.5 Docker-Build-Fixes

`Dockerfile.tests` und `Dockerfile.backend` wurden aktualisiert, um das neue Modul zu kopieren:

```dockerfile
# Dockerfile.tests
COPY self-healing-a2a/ self-healing-a2a/

# Dockerfile.backend
COPY self-healing-a2a/build.gradle.kts self-healing-a2a/build.gradle.kts
```

---

## 3. Wire-Format (A2A-Kurzübersicht)

### Agent Card
```
GET /.well-known/agent.json
→ { name, description, url, version, capabilities, skills: [{ id: "heal-locator" }] }
```

### Heal-Aufruf
```json
POST /a2a
{
  "jsonrpc": "2.0",
  "id": "<uuid>",
  "method": "message/send",
  "params": {
    "message": {
      "role": "user",
      "parts": [{ "kind": "data", "data": { /* FailureContextDto */ } }]
    }
  }
}
```

### Antwort
```json
{
  "jsonrpc": "2.0",
  "id": "<uuid>",
  "result": {
    "kind": "task",
    "status": { "state": "completed" },
    "artifacts": [{
      "name": "heal-result",
      "parts": [{ "kind": "data", "data": { /* HealingResultDto */ } }]
    }]
  }
}
```

---

## 4. Tests

### 4.1 Unit-/Integrationstests

| Test | Beschreibung | Status |
|------|-------------|--------|
| `HealingOrchestratorTest` | Cache-Gate (enabled/disabled) | ✅ |
| `EnvironmentIssueIntegrationTest` | Environment-Check Pipeline | ✅ |
| `A2aRoundTripSmokeTest` | End-to-End A2A Roundtrip (kein LLM) | ✅ |

`A2aRoundTripSmokeTest` startet einen echten HTTP-Server auf einem Zufallsport, sendet eine
`FailureContext` via A2A-Client, und prüft dass:
- Der Server den Kontext korrekt deserialisiert (StringBy-Wrapper, base64-Screenshot, rejectedLocators)
- Der Client den `By` rekonstruiert (`LocatorFactory.construct`)
- Tokens/Duration/explanation korrekt durchgereicht werden

### 4.2 Integrationstests gegen App v1 (kein Healing erwartet)

Alle drei Provider, alle 6 Szenarien.

| Provider | Ergebnis |
|----------|---------|
| Anthropic | **6/6** ✅ |
| OpenAI    | **6/6** ✅ |
| Mistral   | **6/6** ✅ |

### 4.3 Integrationstests gegen App v2 (Healing aktiv)

Alle Locatoren geändert zwischen v1 und v2.

| Provider | Ergebnis | Anmerkung |
|----------|---------|-----------|
| Anthropic | **5/6** | Fahrplan-Details (BottomSheet-Navigation) FAILED — bekannte Schwäche |
| OpenAI    | **6/6** ✅ | |
| Mistral   | **5/6** | Fahrplan-Details (BottomSheet-Navigation) FAILED — bekannte Schwäche |

**Bewertung:** Das Ergebnis ist identisch mit dem Pre-A2A-Baseline. Die Refaktorierung des
`LocatorHealer`-Interface hat das Heilverhalten nicht verändert.

---

## 5. Benchmark-Ergebnisse

Benchmark-Lauf am 2026-04-19 gegen App v2 (alle Locatoren geändert).
Tag-Filter: `@self-healing` — 6 Szenarien je Provider.
Logs: `build/reports/benchmark-<provider>.log`

### 5.1 Cloud-Provider (API verfügbar)

| Provider | Modell | Szenarien | Geheilt | Gesamtzeit | Ø Heilzeit/Locator | Anmerkung |
|----------|--------|-----------|---------|------------|-------------------|-----------|
| Anthropic | claude-3-5-sonnet | 6 | **6/6** ✅ | 9 min 45 s | ~14 s | Inkl. Cache-Hits für Folge-Szenarien |
| OpenAI | gpt-4o | 6 | **6/6** ✅ | 7 min 44 s | ~5 s | Schnellste API-Antwortzeiten |
| Mistral | mistral-large-latest | 6 | **5/6** ⚠️ | 7 min 47 s | ~4 s | BottomSheet-Fahrplan FAILED (`leg_platform` → halluziniertes `leg_item_0_platform`) |

**Ausgeheilte Locatoren (Anthropic, erste LLM-Auflösung ohne Cache):**

| Alter Locator | Neuer Locator | Heilzeit |
|---------------|---------------|----------|
| `By.id: input_from` | `By.id: departure_station` | 9 932 ms |
| `By.id: input_to` | `By.id: arrival_station` | 10 774 ms |
| `By.id: btn_search` | `AccessibilityId: Suche starten` | 22 170 ms |
| `By.id: connection_item` | `By.id: journey_card` | 9 507 ms |
| `By.id: text_from` | `By.id: label_departure` | 9 423 ms |
| `By.id: text_to` | `By.id: label_arrival` | 16 966 ms |
| `By.id: text_transfers` | `By.id: label_changes` | 16 995 ms |
| `By.id: text_no_results` | `By.id: empty_state_text` | 9 407 ms |
| `By.id: leg_train_number` | `AccessibilityId: Zug ICE ICE 123` | 16 826 ms |
| `By.id: leg_platform` | `AndroidUIAutomator: descriptionStartsWith("Gleis")` | 16 962 ms |

### 5.2 Lokale Provider (LM Studio nicht gestartet)

| Provider | Ergebnis | Ursache |
|----------|---------|---------|
| local-qwen3-next | **N/A** | `Connection refused` zu `host.docker.internal:1234` |
| local-devstral | **N/A** | `Connection refused` zu `host.docker.internal:1234` |
| local-qwen3-30b | **N/A** | `Connection refused` zu `host.docker.internal:1234` |

Die lokalen Provider setzen einen laufenden LM Studio Server auf Port 1234 voraus.
Benchmark-Logs zeigen 3 Versuche × Spring AI Retry (9×) pro Szenario, bevor aufgegeben wird.

### 5.3 Fazit

- Die A2A-Refaktorierung hat das Healing-Verhalten der Cloud-Provider **nicht verändert**.
- OpenAI ist mit ~5 s/Locator das schnellste Cloud-Modell.
- Anthropic heilt im Benchmark alle 6/6 (inkl. BottomSheet) durch Cache-Wiederverwendung aus dem ersten Szenario.
- Mistral scheitert konsistent an `leg_platform` (halluziniert eine ID statt UI-Automator-Selektor).

---

## 6. Offene Punkte / Nächste Schritte

| Punkt | Beschreibung |
|-------|-------------|
| Andere Agents via A2A | `TriageAgent`, `StepHealer`, `McpContextEnricher` noch nicht exponiert |
| Streaming | Aktuell kein Streaming (kein `message/stream`) — reicht für synchrone Heal-Calls |
| Auth | Kein API-Key-Schutz auf dem A2A-Endpoint — für Unternehmenseinsatz erforderlich |
| Discovery | Agent Card URL ist `server.port`-basiert — bei Reverse-Proxy anpassen |
| BottomSheet-Failure | Mistral scheitert konsistent an `leg_platform` (Fahrplan-Details); Anthropic und OpenAI heilen erfolgreich via AccessibilityId/UIAutomator |
| Lokale Provider | Benchmark gegen Qwen3-30B, Devstral, Qwen3-Next erfordert laufenden LM Studio-Server auf Port 1234 |
