# Phase 5 – A2A (Agent2Agent) Integration: Änderungsprotokoll

**Datum:** 2026-04-19
**Branch:** `claude/wonderful-banzai-3327ab`
**Autor:** Claude Sonnet 4.6 (automatisiert)

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

## 5. Verhaltens-Verifikation und Bug-Fixes

Nach der A2A-Refaktorierung wurde das Healing-Verhalten gegen App v2 (alle Locatoren geändert) verifiziert. Der Lauf vom 2026-04-19 zeigte: das `LocatorHealer`-Interface verändert das Heilverhalten **nicht** — Cloud-Anthropic/OpenAI bleiben bei 6/6, Cloud-Mistral scheiterte vor den Retry-Fixes (s.u.) noch an `leg_platform`.

> Aktuelle, kanonische Benchmark-Zahlen siehe [TEST-RESULTS.md](../TEST-RESULTS.md). Dieser Abschnitt dokumentiert nur den Refaktorierungs- und Bug-Fix-Verlauf.

### 5.1 Bug-Fixes: Retry-Mechanismus (Post-Benchmark)

Beim Benchmark-Lauf wurden zwei zusammenhängende Bugs im Retry-Mechanismus entdeckt.

#### Bug 1 — Cache-Poisoning bei Retries

Wenn der LLM einen nicht-existenten Locator halluziniert (Mistral → `leg_item_0_platform`),
cached ihn der `HealingOrchestrator` als „erfolgreich" (aus LLM-Sicht war er es). Der Driver
merkt erst bei `findElement`, dass der Locator am UI nicht auflöst, und startet einen Retry
mit `rejectedLocators=[leg_item_0_platform]`. **Der Orchestrator ignorierte diesen Kontext**,
weil der Cache-Key nur den ursprünglichen `failedLocator` enthielt — alle 3 Retries bekamen
dieselbe falsche Antwort aus dem Cache, der LocatorHealer sah die Rejection-Liste nie.

**Fix** (`HealingOrchestrator.attemptHealing`):
- Wenn `rejectedLocators` nicht leer → Cache-Lookup überspringen + stalen Eintrag invalidieren
- `PromptCache.invalidate(key)` neu hinzugefügt
- Test `retryWithRejectedLocators_bypassesCache_andInvalidatesStaleEntry` ergänzt

#### Bug 2 — `InvalidSelectorException` brach Retry-Loop ab

Anthropic halluzinierte UIAutomator-Methoden die nicht existieren
(`contentDescriptionStartsWith`, `contentDescriptionMatches`). Diese erzeugen eine
`InvalidSelectorException` statt `NoSuchElementException`. Der Retry-Catch-Block fing
nur `NoSuchElementException` — der Loop brach sofort ab, keine weitere Chance für den LLM.

**Fix** (`SelfHealingAppiumDriver.attemptHealAndRetry`):
- `catch (NoSuchElementException | InvalidSelectorException retryFail)` — beide Fälle
  landen jetzt im Retry-Pfad und registrieren den schlechten Locator als rejected

#### Bestätigte Post-Fix Ergebnisse (v2, alle 6 Szenarien)

| Provider | Ergebnis | BottomSheet-Heilung |
|----------|---------|-------------------|
| Mistral | **6/6** ✅ | `leg_platform` attempt 2 → `accessibilityId: Gleis 9` |
| Anthropic | **6/6** ✅ | `leg_train_number` attempt 3 → `descriptionContains("Zug ")` |
| OpenAI | **6/6** ✅ | Direkt beim 1. Versuch korrekt |

Alle drei Provider heilen jetzt zuverlässig alle 6 Szenarien, auch ohne Benchmark-Cache-Warmup.

---

## 6. Offene Punkte / Nächste Schritte

| Punkt | Beschreibung |
|-------|-------------|
| Andere Agents via A2A | `TriageAgent`, `StepHealer`, `McpContextEnricher` noch nicht exponiert |
| Streaming | Aktuell kein Streaming (kein `message/stream`) — reicht für synchrone Heal-Calls |
| Auth | Kein API-Key-Schutz auf dem A2A-Endpoint — für Unternehmenseinsatz erforderlich |
| Discovery | Agent Card URL ist `server.port`-basiert — bei Reverse-Proxy anpassen |
| BottomSheet-Failure | ~~Offener Punkt~~ Behoben: alle drei Provider heilen 6/6 nach Retry-Bugfixes (siehe 5.1) |
| Lokale Provider | Benchmark gegen Qwen3-30B, Devstral, GLM-4.7-Flash erfordert laufenden LM Studio-Server auf Port 1234 |
