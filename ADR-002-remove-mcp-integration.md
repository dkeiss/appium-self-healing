# ADR-002: MCP-Integration entfernen — Decorator-only

**Status:** Accepted
**Date:** 2026-04-11
**Deciders:** Daniel Keiss
**Supersedes:** [ADR-001](ADR-001-appium-self-healing-architecture.md) (Hybrid-Ansatz Decorator + MCP)

## Context

[ADR-001](ADR-001-appium-self-healing-architecture.md) entschied sich für einen
**Hybrid-Ansatz**: nativer Appium-Java-Client für die Test-Ausführung (Decorator)
**plus** offizieller `appium/appium-mcp`-Server als Sidecar für das Healing.
Der `McpContextEnricher` sollte als optionale Stage 0 in der Pipeline laufen
und über LLM-Tool-Calling zusätzlichen Kontext (Screenshot, Page Source,
Element-Exploration) für das Healing sammeln.

In der Praxis hat sich diese Schicht als überflüssig — und für das Kernziel
des Projekts sogar als kontraproduktiv — herausgestellt.

### Beobachtungen aus der Implementierung

1. **Der Enricher war ein No-Op.** `McpContextEnricher.parseEnrichmentResult()`
   gab den ursprünglichen `FailureContext` zurück, ohne das Tool-Call-Ergebnis
   zu integrieren. Die Stage 0 war eingebaut, lief optional, und hatte
   funktional keine Wirkung.
2. **Die Dependency war nie vorhanden.** Das `mcp`-Spring-Profil in
   `application-selfhealing.yml` konfigurierte `spring.ai.mcp.client.stdio`,
   aber `spring-ai-starter-mcp-client` wurde nie zu den Gradle-Dependencies
   hinzugefügt. Ohne MCP-Client-Bean wurde der `@ConditionalOnBean(ToolCallbackProvider.class)`
   nie erfüllt — die ganze MCP-Schicht war "verdrahtet, aber nirgends angeschlossen".
3. **Der Decorator liefert bereits den vollständigen Kontext.** Der
   `SelfHealingAppiumDriver` sammelt im `FailureContext` atomar:
   Exception, Page Source XML, Screenshot, kaputter Locator, Page-Object-Source
   und Step-Definition-Source. Es gibt nichts, was MCP zusätzlich beitragen
   könnte, das nicht schon im `FailureContext` steht.

## Decision

**Wir entfernen die MCP-Integration vollständig** und etablieren den
Decorator-only-Ansatz (Option C aus ADR-001) als die offizielle Architektur.

### Konkret entfernt
- `de.keiss.selfhealing.core.healing.McpContextEnricher` (gelöscht)
- `SelfHealingProperties.Mcp` Configuration-Record (entfernt)
- `mcpContextEnricher`-Bean inkl. `@ConditionalOnBean(ToolCallbackProvider.class)` (entfernt)
- `enrichContext()`-Methode + `McpContextEnricher`-Feld im `HealingOrchestrator` (entfernt)
- `mcp:`-Block und `mcp`-Spring-Profil in `application-selfhealing.yml` (entfernt)
- `appium-mcp`-Service aus `docker/docker-compose.yml` (entfernt)
- Alle MCP-Referenzen in `README.md` (Mermaid-Diagramme, Tabellen, Doku)

### Was bleibt
- Decorator-Pattern auf `AppiumDriver` (`SelfHealingAppiumDriver`)
- Atomare Kontext-Sammlung in `FailureContext`
- Pipeline: Triage → Locator/Step/Environment/Bug-Handler → Post-Processing
- Spring-AI-`ChatClient` mit Multi-Provider (Anthropic/OpenAI/Mistral/Local)

## Begründung

### 1. Healing ist eine *bounded task*, kein Exploration-Problem

MCP/Tool-Calling glänzt dort, wo der Agent **nicht im Voraus weiß**, welche
Daten er braucht — Coding-Assistants, Chat-Agents, exploratives Reasoning.
Locator-Healing ist das Gegenteil: das Datenset ist komplett vordefiniert
(Exception, Page Source, Screenshot, Page-Object-Source, Step-Source).
Der Decorator sammelt es atomar zum Zeitpunkt des Fehlers. Es gibt nichts
zu *explorieren*.

### 2. Tool-Loops kollidieren direkt mit dem Benchmarking-Ziel

Das `benchmark/`-Modul ist Kernziel des Projekts. Es soll messen, **wie gut
verschiedene LLMs Locator heilen** — unter möglichst kontrollierten,
reproduzierbaren Bedingungen. MCP würde diese Messung verzerren:

- **Nicht-Determinismus:** Tool-Sequenzen variieren zwischen Läufen → identische
  Fehler produzieren unterschiedliche Healings → keine reproduzierbaren Metriken.
- **Signal-Vermischung:** Statt "Wie gut heilt LLM X?" misst man "Wie geschickt
  orchestriert LLM X Tools?" — das sind zwei verschiedene Fähigkeiten, und
  letztere überlagert die erste.
- **`PromptCache` wird wertlos:** der Cache funktioniert nur bei deterministischen
  Prompts. Tool-Loops mit variabler Schritt-Reihenfolge haben keine stabile
  Cache-Key-Bildung mehr.

### 3. Performance- und Kosten-Overhead ohne Mehrwert

- **Latenz:** Jeder MCP-Tool-Call ist ein zusätzlicher LLM-Roundtrip.
  Triage + Locator-Heal hat aktuell 2 LLM-Calls. Mit Tool-Loops können daraus
  schnell 5–10 Calls pro Heal werden.
- **Token-Kosten:** 45+ MCP-Tools im System-Prompt blasen den Kontext auf,
  noch bevor die eigentliche Aufgabe beschrieben ist.
- **Sidecar-Komplexität:** Node-Prozess (`appium-mcp`) im Spring/Java-Stack
  ist operative Schuld — Lifecycle, Healthchecks, Versions-Drift, eine weitere
  Failure-Quelle in Docker-Compose.

### 4. Der Decorator hat den optimaleren Datenpfad

Vergleich der Datenpfade für "hole die aktuelle Page Source":

| Pfad | Hops |
|---|---|
| **Decorator (heute)** | `driver.getPageSource()` — 1 Java-Call |
| **Über MCP** | Java → Spring-AI-MCP-Client → JSON-RPC → Node-Sidecar → Appium HTTP → Appium Server — 5 Hops |

Für eine Aufgabe, bei der die benötigten Daten ohnehin bekannt sind, ist
der direkte Pfad strikt überlegen.

## Consequences

### Was einfacher wird
- **Eine Komponente weniger zu pflegen:** Kein `McpContextEnricher`,
  kein MCP-Spring-Profil, kein `appium-mcp`-Sidecar, kein Node-Prozess
  im Docker-Compose.
- **Reproduzierbares Benchmarking:** LLM-Vergleiche messen ausschließlich
  die Healing-Fähigkeit, ohne Tool-Orchestration als Störvariable.
- **Stabiler `PromptCache`:** Deterministische Prompts → effektive Cache-Hits.
- **Klarerer Architektur-Erzählstrang** für Talks/Docs: "Decorator sammelt
  alles, ein LLM-Call heilt" — statt "Hybrid mit optionaler MCP-Schicht,
  die manchmal aktiv ist".

### Was schwieriger wird
- **Iteratives Healing entfällt:** Wenn ein LLM beim ersten Versuch einen
  schlechten Locator vorschlägt, gibt es keinen In-Loop-Verifizierungs-Schritt
  via MCP-Tools. Das wird bewusst akzeptiert — der `SelfHealingAppiumDriver`
  hat eine `maxRetries`-Schleife, die einen falschen Heal beim nächsten
  Anlauf erneut versuchen kann.
- **Kein Multi-File-Refactoring:** Wenn künftig mal mehrere Page Objects
  parallel angepasst werden müssen, braucht es entweder einen erweiterten
  `StepHealer` oder einen anderen Mechanismus — kein automatisches Tool-Calling
  über das Filesystem.

### Was später revisited werden kann

MCP ist **nicht prinzipiell ausgeschlossen**. Die Tür bleibt offen für:

- **MCP-Track im Benchmark:** Eine eigenständige Track-Variante "MCP vs Direct",
  die den Effekt von Tool-Loops auf Healing-Qualität *kontrolliert* misst —
  also als Untersuchungsobjekt, nicht als Default.
- **Externe Integrationen via MCP:** Bug-Reporter, der Tickets in Linear/Jira
  anlegt, oder Slack-Notifications. Solche Integrationen passen zum
  MCP-Pattern (externes System, klare Tool-Surface) und haben keinen
  Einfluss auf die Healing-Hot-Paths.
- **Vision-Model-Integration:** Wenn screenshot-basiertes Healing dazukommt,
  kann das ein eigener spezialisierter MCP-Server sein.

## Trade-off Analysis (Update zu ADR-001)

| Kriterium | Hybrid (ADR-001) | Decorator-only (ADR-002) |
|---|---|---|
| Architekturkomplexität | Mittel | Niedrig |
| LLM-Calls pro Heal | 2–10 (variabel) | 2 (konstant) |
| Reproduzierbarkeit | Mittel | Hoch |
| Benchmarking-Qualität | Verzerrt durch Tool-Loops | Sauberes Signal |
| Operative Komplexität | Hoch (Node-Sidecar) | Niedrig |
| Healing-Qualität bei großen Page-Sources | Potenziell besser | Begrenzt durch Truncation (15k chars) |
| Cache-Effektivität | Niedrig | Hoch |

## Action Items

- [x] `McpContextEnricher.java` löschen
- [x] `SelfHealingProperties.Mcp` entfernen
- [x] `SelfHealingAutoConfiguration` MCP-Bean + Imports aufräumen
- [x] `HealingOrchestrator` Stage 0 entfernen
- [x] `EnvironmentIssueIntegrationTest` Konstruktor-Args anpassen
- [x] `application-selfhealing.yml` MCP-Block + Profil entfernen
- [x] `docker-compose.yml` `appium-mcp`-Service entfernen
- [x] `README.md` Mermaid + Tabellen + Konfig-Sektionen bereinigen
- [x] ADR-001 als Superseded markieren
- [x] ADR-002 schreiben (dieses Dokument)
- [ ] Künftiger Verification-Agent (in-process, ohne MCP) — offen
