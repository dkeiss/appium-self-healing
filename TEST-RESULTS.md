# Test-Ergebnisse

> Letzte Ausführung: **17.04.2026** — Devstral Small 2 lokal: 6/6 auf @very-hard-navigation, alle Heals im ersten Versuch

## Inhaltsverzeichnis

- [MCP vs. no-MCP Benchmark](#mcp-vs-no-mcp-benchmark)
- [Zusammenfassung](#zusammenfassung)
- [Test-Runs im Detail](#test-runs-im-detail)
  - [v1 Baseline (Anthropic)](#v1-baseline-anthropic)
  - [v2 Self-Healing (Anthropic)](#v2-self-healing-anthropic)
  - [v2 Self-Healing (OpenAI / GPT-4.1)](#v2-self-healing-openai--gpt-41)
  - [v2 Self-Healing (Mistral / Codestral)](#v2-self-healing-mistral--codestral)
  - [v2 Self-Healing (Lokal / Qwen3-Coder-30B)](#v2-self-healing-lokal--qwen3-coder-30b)
  - [v2 Self-Healing (Lokal / Devstral Small 2)](#v2-self-healing-lokal--devstral-small-2)
  - [v2 Self-Healing (Lokal / GLM-4.7-Flash)](#v2-self-healing-lokal--glm-47-flash)
- [Lokale LLMs im Direktvergleich](#lokale-llms-im-direktvergleich)
- [LLM-Vergleich](#llm-vergleich)
- [verify-fix.sh Validierung](#verify-fixsh-validierung)
- [Cucumber Reports](#cucumber-reports)
- [Highlight-Feature (Rote Markierung)](#highlight-feature-rote-markierung)

---

## MCP vs. no-MCP Benchmark

> Track: `@very-hard-navigation` · App v2 · Cache disabled · 2 Runs/Provider (nomcp + mcp)
> Detailbericht: [docs/mcp-comparison-report.md](docs/mcp-comparison-report.md)

### Ergebnisübersicht

| Provider | MCP | Tests | Build | Gesamtdauer | Heals | ∅ Heal-Zeit | Tokens |
|---|---|---|---|---|---|---|---|
| Anthropic (claude-sonnet-4-6) | ✗ | **6/6** | ✅ | 14 min 48 s | 31 | 12 085 ms | 313 133 |
| Anthropic (claude-sonnet-4-6) | ✓ | **6/6** | ✅ | 23 min 40 s | 31 | 15 656 ms | 332 491 |
| OpenAI (gpt-4.1) | ✗ | **6/6** | ✅ |  9 min 54 s | 31 |  5 265 ms | 254 565 |
| OpenAI (gpt-4.1) | ✓ | **6/6** | ✅ | 23 min 11 s | 31 | 20 400 ms | 256 917 |
| Mistral (codestral-latest) | ✗ | **5/6** | ❌ |  9 min 27 s | 36 |  3 775 ms | 301 450 | ¹ |
| Mistral (codestral-latest) | ✓ | **6/6** | ✅ |  9 min 37 s | 31 |  4 079 ms | 287 653 |

> ¹ Mistral+nomcp scheiterte ursprünglich am `leg_platform`-Locator — behoben durch `rejectedLocators`-Fix (siehe unten).

### Abgesicherte nomcp-Baseline (Rerun 17.04.2026)

| Provider | Tests | Build | Heals | ∅ Heal-Zeit |
|---|---|---|---|---|
| Anthropic | **6/6** | ✅ | 33 | 12 300 ms |
| OpenAI | **6/6** | ✅ | 31 |  5 277 ms |
| Mistral | **5/6** | ❌ | 36 |  3 653 ms |

Alle nomcp-Runs stabil — Fixes (HTTP-429-Retry-Config, Prompt-Korrektur) brechen nichts.

### rejected-locators Fix (17.04.2026)

`FailureContext` trackt jetzt fehlgeschlagene Heal-Vorschläge über Retries hinweg.
Mistral erfand bei Attempt 1 `leg_item_0_platform` (existiert nicht) und wiederholte es in Attempt 2+3, weil der Kontext den Original-Locator durch den fehlgeschlagenen Vorschlag ersetzte.
Nach dem Fix zeigt der Prompt ab Attempt 2: _"Already tried — NOT found: leg_item_0_platform"_ und Mistral schlägt `accessibilityId: Gleis 9` vor.

| Provider | Tests (vor Fix) | Tests (nach Fix) |
|---|---|---|
| Anthropic | 6/6 ✅ | 6/6 ✅ |
| OpenAI | 6/6 ✅ | 6/6 ✅ |
| Mistral | **5/6 ❌** | **6/6 ✅** |

### Devstral Small 2 lokal (17.04.2026)

| Provider | Tests | Build | Heals | ∅ Heal-Zeit | Tokens | Gesamtdauer |
|---|---|---|---|---|---|---|
| Devstral Small 2 (lokal, RTX 3090) | **6/6** | ✅ | 31 | 100 344 ms | 285 513 | 70 min 9 s |

- Alle 31 Heals im **ersten Versuch** — kein Retry, keine Halluzination
- `leg_platform` → `accessibilityId: Gleis 9` direkt korrekt (Cloud-Mistral braucht 2 Attempts)
- `rejectedLocators`-Fix wurde nicht benötigt — Devstral halluziniert keine nicht-existenten IDs
- ~20× langsamer als Cloud-Provider, aber kostenlos (lokale GPU)

### Fazit

- **MCP liefert keinen Mehrwert** unter den aktuellen Bedingungen: `appium-mcp` kann die laufende Appium-Session nicht nutzen, jeder Tool-Call gibt "No driver found" zurück.
- **MCP ist stabil** (nach den Fixes): kein `NonTransientAiException` bei OpenAI, kein Tool-Dispatch-Fehler bei Mistral.
- **Overhead:** Anthropic +9 min/Run, OpenAI +13 min/Run, Mistral <1 min/Run.
- `self-healing.mcp.enabled` bleibt `false` (Default). MCP-Profil-Support bleibt für künftige Session-Sharing-Experimente erhalten.

---

## Zusammenfassung

| Test | Provider | Szenarien | Ergebnis | Dauer |
|------|----------|-----------|----------|-------|
| `./run-tests.sh v1` | Anthropic | 5/5 PASSED | Kein Healing nötig | ~2m 51s |
| `./run-tests.sh v2` | Anthropic (Claude Sonnet) | 5/5 PASSED | 8 Locator geheilt | ~8m 50s |
| `./run-tests.sh v2 openai` | OpenAI (GPT-4.1) | 5/5 PASSED | 8 Locator geheilt | ~7m 53s |
| `./run-tests.sh v2 mistral` | Mistral (Codestral) | 5/5 PASSED | 8 Locator geheilt | ~7m 52s |
| `SPRING_PROFILES_ACTIVE=local-qwen3-30b` | LM Studio (Qwen3-Coder-30B) | 4/5 PASSED | 7/8 Locator geheilt | ~19m 39s |
| `SPRING_PROFILES_ACTIVE=local-devstral` | LM Studio (Devstral Small 2) | 5/5 PASSED | 8/8 Locator geheilt | ~24m 37s |
| `SPRING_PROFILES_ACTIVE=local-glm-4-7-flash` | LM Studio (GLM-4.7-Flash) | **0/5 FAILED** | 1/2 versuchte Heals korrekt, 6 nie versucht (Cache-Kaskade) | ~9m 13s |
| `./verify-fix.sh` | Anthropic | Baseline + Fix PASSED | Infra-Optimierung validiert | ~15m |

**Alle 3 Cloud-Provider heilen sämtliche 8 Locator-Änderungen erfolgreich. Devstral Small 2 erreicht als lokales Modell ebenfalls 8/8. Qwen3-Coder-30B heilt 7/8 — `text_to` wird als nicht-existenter `label_to` halluziniert. GLM-4.7-Flash schlägt für `input_to` das Label `Ankunftsbahnhof` statt das Eingabefeld vor — der Cache propagiert den falschen Heal in alle weiteren Szenarien, nichts läuft mehr.**

---

## Test-Runs im Detail

### v1 Baseline (Anthropic)

> Tests gegen die v1-App — alle Locatoren stimmen, kein Self-Healing.

```
╔══════════════════════════════════════════════════╗
║  App Version: v1                                 ║
║  LLM Provider: anthropic                         ║
║  Dauer: 2m 51s                                   ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis | Self-Healing |
|---|----------|----------|-------------|
| 1 | Direkte Verbindung finden | PASSED | Nicht nötig |
| 2 | Verbindung mit Umstieg | PASSED | Nicht nötig |
| 3 | Keine Verbindung gefunden | PASSED | Nicht nötig |
| 4 | Einfache ID-Änderung wird geheilt | PASSED | 0 Healings, 0 Cache |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | PASSED | 0 Healings, 0 Cache |

**Self-Healing Report (v1):**
```
║  No healing was needed — all locators matched.  ║
║  Total healings: 0
║  Cache hits:     0
║  Cache misses:   0
```

---

### v2 Self-Healing (Anthropic)

> Tests gegen die v2-App — alle 8 Locatoren werden durch Claude Sonnet geheilt.

```
╔══════════════════════════════════════════════════╗
║  App Version: v2                                 ║
║  LLM Provider: anthropic (claude-sonnet-4-6)     ║
║  Dauer: 8m 50s                                   ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis |
|---|----------|----------|
| 1 | Direkte Verbindung finden | PASSED |
| 2 | Verbindung mit Umstieg | PASSED |
| 3 | Keine Verbindung gefunden | PASSED |
| 4 | Einfache ID-Änderung wird geheilt | PASSED |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | PASSED |

**Geheilte Locatoren:**

| v1-Locator (Page Object) | Geheilt zu (v2) | Strategie |
|--------------------------|-----------------|-----------|
| `input_from` | `departure_station` | `By.id` |
| `input_to` | `arrival_station` | `By.id` |
| `btn_search` | `fab_search` | `By.id` |
| `connection_item` | `journey_card` | `By.id` |
| `text_from` | `label_departure` | `By.id` |
| `text_to` | `label_arrival` | `By.id` |
| `text_transfers` | `label_changes` | `By.id` |
| `text_no_results` | `empty_state_text` | `By.id` |

**Self-Healing Report:**
```
║  Total healings: 0         (nach dem ersten Durchlauf im Cache)
║  Cache hits:     11
║  Cache misses:   8          (8 einzigartige Locatoren)
║  Cache Hit-Rate: 58%
```

---

### v2 Self-Healing (OpenAI / GPT-4.1)

> Tests gegen die v2-App — alle 8 Locatoren werden durch GPT-4.1 geheilt.

```
╔══════════════════════════════════════════════════╗
║  App Version: v2                                 ║
║  LLM Provider: openai (gpt-4.1)                 ║
║  Dauer: 7m 53s                                   ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis |
|---|----------|----------|
| 1 | Direkte Verbindung finden | PASSED |
| 2 | Verbindung mit Umstieg | PASSED |
| 3 | Keine Verbindung gefunden | PASSED |
| 4 | Einfache ID-Änderung wird geheilt | PASSED |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | PASSED |

**Self-Healing Report:**
```
║  Cache hits:     16
║  Cache misses:   8
║  Cache Hit-Rate: 67%
║
║  Cached locator mappings:
║    By.id: input_to → id("arrival_station")
║    By.id: input_from → id("departure_station")
║    By.id: text_to → id("label_arrival")
║    By.id: text_from → id("label_departure")
║    By.id: btn_search → id("fab_search")
║    By.id: text_no_results → id("empty_state_text")
║    By.id: text_transfers → id("label_changes")
║    By.id: connection_item → id("journey_card")
```

---

### v2 Self-Healing (Mistral / Codestral)

> Tests gegen die v2-App — alle 8 Locatoren werden durch Codestral geheilt.

```
╔══════════════════════════════════════════════════╗
║  App Version: v2                                 ║
║  LLM Provider: mistral (codestral-latest)        ║
║  Dauer: 7m 52s                                   ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis |
|---|----------|----------|
| 1 | Direkte Verbindung finden | PASSED |
| 2 | Verbindung mit Umstieg | PASSED |
| 3 | Keine Verbindung gefunden | PASSED |
| 4 | Einfache ID-Änderung wird geheilt | PASSED |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | PASSED |

**Interessant:** Mistral wählt für `btn_search` eine **andere Healing-Strategie** als Anthropic/OpenAI:

| Locator | Anthropic / OpenAI | Mistral |
|---------|-------------------|---------|
| `btn_search` | `By.id("fab_search")` | `AppiumBy.accessibilityId("Suche starten")` |

Alle anderen Locatoren werden identisch geheilt.

**Self-Healing Report:**
```
║  Cache hits:     16
║  Cache misses:   8
║  Cache Hit-Rate: 67%
║
║  Cached locator mappings:
║    By.id: connection_item → id("journey_card")
║    By.id: text_transfers → id("label_changes")
║    By.id: text_no_results → id("empty_state_text")
║    By.id: btn_search → accessibilityId("Suche starten")
║    By.id: text_from → id("label_departure")
║    By.id: text_to → id("label_arrival")
║    By.id: input_from → id("departure_station")
║    By.id: input_to → id("arrival_station")
```

**Detaillierte Step-Zeiten (letzter Mistral-Lauf):**

| Szenario | Step | Dauer |
|----------|------|-------|
| Direkte Verbindung finden | Startbahnhof eingeben | 27.3s |
| Direkte Verbindung finden | Zielbahnhof eingeben | 23.0s |
| Direkte Verbindung finden | Suchen klicken | 23.4s |
| Direkte Verbindung finden | Verbindung prüfen | 21.8s |
| Direkte Verbindung finden | Start prüfen | 22.3s |
| Direkte Verbindung finden | Ziel prüfen | 22.8s |
| Verbindung mit Umstieg | Startbahnhof eingeben | 12.7s |
| Verbindung mit Umstieg | Zielbahnhof eingeben | 12.1s |
| Verbindung mit Umstieg | Suchen klicken | 14.9s |
| Verbindung mit Umstieg | Verbindung prüfen | 11.2s |
| Verbindung mit Umstieg | Umstieg prüfen | 20.6s |
| Keine Verbindung gefunden | Startbahnhof eingeben | 12.1s |
| Keine Verbindung gefunden | Zielbahnhof eingeben | 11.3s |
| Keine Verbindung gefunden | Suchen klicken | 12.5s |
| Keine Verbindung gefunden | Meldung prüfen | 20.2s |

> **Hinweis:** Das erste Szenario ist langsamer (~23s/Step vs. ~12s/Step), weil dort die LLM-Calls stattfinden. Folge-Szenarien nutzen den In-Memory-Cache und sind deutlich schneller.

---

### v2 Self-Healing (Lokal / Qwen3-Coder-30B)

> Tests gegen die v2-App mit einem lokal laufenden Modell — LM Studio auf Windows 11, RTX 3090 (24 GB VRAM).

```
╔══════════════════════════════════════════════════╗
║  App Version: v2                                 ║
║  LLM Provider: local-qwen3-30b (LM Studio)       ║
║  Modell: qwen/qwen3-coder-30b (18.63 GB MoE)     ║
║  Backend: OpenAI-kompatible REST-API             ║
║  Dauer: 19m 39s                                  ║
║  Lauf: 2026-04-15 14:32–14:53                    ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis | Dauer |
|---|----------|----------|-------|
| 1 | Direkte Verbindung finden | **FAILED** | 770 s |
| 2 | Verbindung mit Umstieg | PASSED | 157 s |
| 3 | Keine Verbindung gefunden | PASSED | 130 s |
| 4 | Einfache ID-Änderung wird geheilt | PASSED | 50 s (Cache) |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | PASSED | 63 s (Cache) |

**Geheilte Locatoren (7/8 korrekt):**

| v1-Locator | Vorschlag Qwen3-30B | Strategie | Ergebnis |
|------------|---------------------|-----------|----------|
| `input_from` | `departure_station` | `By.id` | OK |
| `input_to` | `arrival_station` | `By.id` | OK |
| `btn_search` | `Suche starten` | `accessibilityId` | OK |
| `connection_item` | `journey_card` | `By.id` | OK |
| `text_from` | `Berlin Hbf` | `accessibilityId` | text-basiert, nicht generisch |
| `text_to` | `label_to` | `By.id` | halluziniert, existiert nicht |
| `text_transfers` | `label_changes` | `By.id` | OK |
| `text_no_results` | `Keine Verbindungen gefunden` | `accessibilityId` | OK |

**Token- & Latenz-Metriken pro Heal-Call:**

| Phase | Prompt Tokens | Completion Tokens | Total | Latenz |
|-------|---------------|-------------------|-------|--------|
| Triage | ~2.600 | ~100 | ~2.700 | ~15–30 s |
| LocatorHealer | ~5.000–6.000 | ~500–750 | ~5.500–6.800 | ~60–80 s |
| **Gesamt pro Locator** | **~7.600–8.600** | **~600–850** | **~8.200–9.500** | **~75–110 s** |

Triage-Confidence war bei allen Fällen konstant **0.95** mit Kategorie `LOCATOR_CHANGED`.

**Fehlerfall `text_to` im Detail:**

```
14:43:15  Healing attempt 1/3 for: By.id: text_to
14:44:31  Healed locator: By.id: text_to → By.id: label_to (75 s)
           → label_to existiert nicht in v2-XML
14:44:41  Healing attempt 2/3 for: By.id: text_to
14:46:14  Healed locator: By.id: label_to → By.id: label_to (74 s)
           → identischer Vorschlag im Retry
14:46:24  Healing attempt 3/3 for: By.id: text_to
           → max-retries erschöpft → Szenario FAILED
```

> **Root-Cause-Hypothese:** Das Modell sieht in der v2-XML `label_departure`/`label_arrival` als Muster und extrapoliert fälschlich zu `label_to`. Im Retry wird derselbe Vorschlag wiederholt, weil der Prompt nicht explizit ausschließt, bereits fehlgeschlagene Locatoren erneut vorzuschlagen.

**Bugs, die für diesen Lauf gefixt werden mussten:**

1. **`CucumberSpringConfig.java`** — hardcodierter `spring.autoconfigure.exclude` für OpenAI + Mistral entfernt (verhinderte die Bean-Erzeugung für LM Studio, obwohl LM Studio der OpenAI-Client ist)
2. **`application-selfhealing.yml`** — `/v1` aus `base-url` entfernt (Spring AI hängt es selbst an → POST ging an `/v1/v1/chat/completions`, LM Studio antwortete mit HTTP 200 und leerem Body, Spring AI loggte "No choices returned" und `ChatResponse.getResult()` war null)
3. **`TestConfig.java`** — `setNewCommandTimeout(Duration.ofMinutes(10))` ergänzt (Default von 60 s killte die Appium-Session während der 75-s-LocatorHeal-Calls)

---

### v2 Self-Healing (Lokal / Devstral Small 2)

> Zweiter Lauf mit lokalem LLM — Mistral Devstral Small 2 (2512) auf derselben LM-Studio-Installation, RTX 3090 (24 GB VRAM).

```
╔══════════════════════════════════════════════════╗
║  App Version: v2                                 ║
║  LLM Provider: local-devstral (LM Studio)        ║
║  Modell: mistralai/devstral-small-2-2512         ║
║  Backend: OpenAI-kompatible REST-API             ║
║  Dauer: 24m 37s                                  ║
║  Lauf: 2026-04-15 18:02–18:26                    ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis | Dauer |
|---|----------|----------|-------|
| 1 | Direkte Verbindung finden | PASSED | 976 s (6 Heals) |
| 2 | Verbindung mit Umstieg | PASSED | 201 s (1 Heal + Cache) |
| 3 | Keine Verbindung gefunden | PASSED | 174 s (1 Heal + Cache) |
| 4 | Einfache ID-Änderung wird geheilt | PASSED | 52 s (Cache) |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | PASSED | 63 s (Cache) |

**Geheilte Locatoren (8/8 korrekt, jeweils im 1. Versuch):**

| v1-Locator | Vorschlag Devstral | Strategie | Latenz | Ergebnis |
|------------|--------------------|-----------|--------|----------|
| `input_from` | `departure_station` | `By.id` | 112.1 s | ✅ |
| `input_to` | `arrival_station` | `By.id` | 115.7 s | ✅ |
| `btn_search` | `Suche starten` | `AppiumBy.accessibilityId` | 115.5 s | ✅ |
| `connection_item` | `journey_card` | `By.id` | 125.6 s | ✅ |
| `text_from` | `label_departure` | `By.id` | 121.8 s | ✅ |
| `text_to` | `label_arrival` | `By.id` | 118.6 s | ✅ |
| `text_transfers` | `label_changes` | `By.id` | 111.1 s | ✅ |
| `text_no_results` | `empty_state_text` | `By.id` | 97.7 s | ✅ |

**Token- & Latenz-Metriken pro Heal-Call:**

| Phase | Prompt Tokens | Completion Tokens | Total | Latenz |
|-------|---------------|-------------------|-------|--------|
| Triage | ~2.580–3.076 | ~73–101 | ~2.658–3.175 | ~23–28 s |
| LocatorHealer | ~4.486–5.840 | ~620–758 | ~5.230–6.506 | ~85–105 s |
| **Gesamt pro Locator** | **~7.100–8.900** | **~700–850** | **~7.900–9.700** | **~97–126 s** |

Triage-Confidence war bei allen Fällen konstant **0.95** mit Kategorie `LOCATOR_CHANGED`. **Keine Retry-Loops, keine Halluzinationen.**

**Vergleich zu Qwen3-Coder-30B (selbe Hardware, selbe App):**

- **Heal-Rate:** Devstral 8/8 vs Qwen3 7/8 (Qwen3 halluzinierte `label_to` für `text_to`).
- **Heal-Konsistenz:** Devstral wählte für `text_from`/`text_to` das sauber generalisierende `label_departure`/`label_arrival`; Qwen3 ging bei `text_from` auf die textbasierte Accessibility-ID `Berlin Hbf` (fragil gegenüber Testdaten) und bei `text_to` in den Retry-Loop.
- **Latenz/Heal:** Devstral ~110 s vs Qwen3 ~75 s — Devstral ist pro Call ~45 % langsamer, produziert dafür aber beim ersten Versuch einen funktionierenden Locator.
- **Gesamtdauer:** Devstral 24m 37s vs Qwen3 19m 39s. Trotz langsamerer Einzel-Calls liegt Devstral nur ~5 min dahinter, weil kein Retry-Zyklus nötig war.

---

### v2 Self-Healing (Lokal / GLM-4.7-Flash)

> Dritter lokaler Lauf — Z.ai's GLM-4.7-Flash, ein **Reasoning-Modell** (separates `reasoning_content`-Feld in der API-Response), via LM Studio auf RTX 3090.

```
╔══════════════════════════════════════════════════╗
║  App Version: v2                                 ║
║  LLM Provider: local-glm-4-7-flash (LM Studio)   ║
║  Modell: zai-org/glm-4.7-flash (Reasoning)       ║
║  Backend: OpenAI-kompatible REST-API             ║
║  Dauer: 9m 13s (Test brach nach Cache-Kaskade ab)║
║  Lauf: 2026-04-16 08:42–08:50                    ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis | Dauer | Anmerkung |
|---|----------|----------|-------|-----------|
| 1 | Direkte Verbindung finden | **FAILED** | 432 s | 2 LLM-Heals, einer falsch |
| 2 | Verbindung mit Umstieg | **FAILED** | 28 s | Sofort-Fail durch Cache |
| 3 | Keine Verbindung gefunden | **FAILED** | 29 s | Sofort-Fail durch Cache |
| 4 | Einfache ID-Änderung wird geheilt | **FAILED** | 28 s | Sofort-Fail durch Cache |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | **FAILED** | 28 s | Sofort-Fail durch Cache |

**Geheilte Locatoren (1 von 2 versuchten korrekt, 6 nie getestet):**

| v1-Locator | Vorschlag GLM-4.7-Flash | Strategie | Latenz | Ergebnis |
|------------|--------------------------|-----------|--------|----------|
| `input_from` | `departure_station` | `By.id` | 152.6 s | ✅ |
| `input_to` | `Ankunftsbahnhof` | `AppiumBy.accessibilityId` | 140.4 s | ❌ Label statt Input-Feld — `sendKeys` wirft `InvalidElementStateException` |
| `btn_search` | — | — | — | nie versucht (Test brach vorher ab) |
| `connection_item` | — | — | — | nie versucht |
| `text_from` | — | — | — | nie versucht |
| `text_to` | — | — | — | nie versucht |
| `text_transfers` | — | — | — | nie versucht |
| `text_no_results` | — | — | — | nie versucht |

**Token- & Latenz-Metriken pro Heal-Call:**

| Phase | Prompt Tokens | Completion Tokens | Total | Latenz |
|-------|---------------|-------------------|-------|--------|
| Triage | ~2.527 | **~957–1.149** | ~3.484–3.676 | ~46–54 s |
| LocatorHealer | ~5.025–5.189 | **~2.030–2.366** | ~7.219–7.391 | ~95–98 s |
| **Gesamt pro Locator** | **~7.500–7.700** | **~3.000–3.500** | **~10.700–11.000** | **~140–153 s** |

Triage-Confidence: **1.0** (input_from) und **0.95** (input_to). Auffällig: GLM ist beim falschen Locator-Vorschlag genauso „selbstsicher" wie beim korrekten.

> **Reasoning-Token-Overhead:** Die Completion-Tokens sind 3-4× höher als bei Qwen3/Devstral, weil GLM-4.7-Flash erst eine ausgedehnte interne Chain-of-Thought im `reasoning_content`-Feld erzeugt, bevor das eigentliche Ergebnis kommt. Im Smoke-Test brauchte GLM **157 Reasoning-Tokens für ein simples „OK"** — `max-tokens` musste deshalb auf 16384 angehoben werden, sonst läuft der Reasoning-Block bevor die Antwort beginnt aus.

**Fehlerfall `input_to` im Detail:**

```
08:45:32  Healing attempt 1/3 for: By.id: input_to
08:46:26  Triage: LOCATOR_CHANGED, confidence 0.95
08:48:46  Healed locator: By.id: input_to → AppiumBy.accessibilityId: Ankunftsbahnhof (140.4 s)
           → "Ankunftsbahnhof" ist die contentDescription des LABELS,
             nicht des Input-Felds. Das Label ist nicht editierbar.
08:49:13  enterDestination("Atlantis") → InvalidElementStateException
08:49:15  Cache liefert in Scenario 2 denselben falschen Heal
           → Scenarios 2-5 scheitern ohne weiteren LLM-Call
```

> **Root-Cause-Hypothese:** GLM hat das semantisch passendste UI-Element gewählt — die Accessibility-Beschriftung „Ankunftsbahnhof" liest sich konzeptuell wie „der Zielbahnhof-Eingang". Es hat aber nicht geprüft, ob das Element interaktiv/editierbar ist. Mit dem Reasoning-Trace könnte man im Prompt explizit „nur Locator von editierbaren Eingabefeldern verwenden" verlangen, und die Selbstsicherheit (confidence 0.95) deutet darauf hin, dass das Modell bei der falschen Wahl gar keine Zweifel hatte.

> **Cache-Kaskade als Test-Methodik-Limit:** Der `PromptCache` ist als In-Memory-Map implementiert und persistiert einen erfolgreichen Heal innerhalb desselben Test-JVM. Er hat keine Möglichkeit zu erkennen, dass der gecachte Locator zwar gefunden wurde, aber semantisch falsch ist (`InvalidElementStateException` wird nicht im `HealingOrchestrator` gefangen, sondern später im Test-Step). Konsequenz: GLM bekam gar nicht die Chance, die übrigen 6 Locatoren zu heilen. Ein Folge-Task zur Implementierung eines `self-healing.cache.enabled=false`-Schalters für faire Benchmarks ist abgelegt.

**Anpassung im Profil (`local-glm-4-7-flash`):**

```yaml
chat:
  options:
    model: zai-org/glm-4.7-flash
    temperature: 0.1
    max-tokens: 16384  # GLM ist Reasoning-Modell — braucht Budget für reasoning_content + content
```

Ohne den hochgesetzten `max-tokens`-Wert würde der Reasoning-Block jeden Response-Body abschneiden, bevor das eigentliche `content` beginnt — Spring AI würde `ChatResponse.getResult()` auf einen leeren String setzen und der Heal wäre ein Parse-Fehler statt eines falschen Locators.

---

## Lokale LLMs im Direktvergleich

| Metrik | Qwen3-Coder-30B | Devstral Small 2 | GLM-4.7-Flash |
|--------|----------------|------------------|---------------|
| **Modelltyp** | Code-Modell | Code-Modell | **Reasoning-Modell** |
| **Modell-ID (LM Studio)** | `qwen/qwen3-coder-30b` | `mistralai/devstral-small-2-2512` | `zai-org/glm-4.7-flash` |
| **Szenarien bestanden** | 4/5 (80 %) | **5/5 (100 %)** | 0/5 (0 %) |
| **Locatoren geheilt** | 7/8 | **8/8** | 1/2 versucht (6 nie aufgerufen) |
| **Test-Dauer** | 19m 39s | 24m 37s | 9m 13s (Abbruch) |
| **Latenz/Heal** | ~75 s | ~110 s | ~145 s |
| **Triage prompt/completion** | ~2.600 / ~100 | ~2.700 / ~85 | ~2.500 / **~1.050** |
| **Healer prompt/completion** | ~5.500 / ~600 | ~5.300 / ~700 | ~5.100 / **~2.200** |
| **Triage Confidence (typ.)** | 0.95 | 0.95 | 0.95–1.0 |
| **Halluzinationen** | `text_to → label_to` (Retry) | keine | `input_to → Ankunftsbahnhof` (Label statt Input) |
| **Hardware** | RTX 3090 (24 GB) | RTX 3090 (24 GB) | RTX 3090 (24 GB) |

**Zusammenfassung der drei lokalen Modelle:**

1. **Devstral Small 2** ist der klare Gewinner für diesen Healing-Task: 100 % Heal-Rate, sauber generalisierende Vorschläge (`label_departure`/`label_arrival`), keine Retry-Loops oder Halluzinationen — auch wenn pro Call ~110 s.
2. **Qwen3-Coder-30B** ist die Speed-Option: ~30 % schneller pro Call als Devstral, 87.5 % Heal-Rate. Einziger systematischer Fehler ist das halluzinierte `label_to` im Retry-Loop bei `text_to`.
3. **GLM-4.7-Flash** ist für diesen Use-Case ungeeignet: das Reasoning-Modell verbraucht 3-4× mehr Completion-Tokens, ist mit ~145 s/Heal das langsamste der drei, und der einzige falsche Heal (`input_to → Ankunftsbahnhof`) reicht aus, um über den Cache alle Folge-Szenarien zu kippen. Für Tasks mit echtem Reasoning-Bedarf (komplexe Triage-Klassifikation, App-Bug-Analyse) wäre GLM evtl. interessanter — für die rein syntaktische Locator-Substitution ist die Reasoning-Phase reine Verschwendung.

**Empfehlung für lokale Setups:** Devstral Small 2 als Default, Qwen3-Coder-30B wenn Latenz wichtiger als Perfektion ist. GLM-4.7-Flash erst wieder evaluieren, wenn der Cache-Bypass-Schalter (siehe Folge-Task) verfügbar ist und der Heal-Prompt explizit „nur editierbare Elemente"-Constraints enthält.

---

## Cache-Bypass für LLM-Benchmarks (`self-healing.cache.enabled`)

Der im GLM-Abschnitt beschriebene Cache-Kaskaden-Effekt ist als „Folge-Task" markiert; dieser ist jetzt umgesetzt. Neu: die Property `self-healing.cache.enabled` (Default `true`, überschreibbar via ENV `SELF_HEALING_CACHE_ENABLED=false`).

**Was passiert bei `false`:**
- `HealingOrchestrator.attemptHealing()` überspringt sowohl `promptCache.get()` als auch `promptCache.put()`.
- Ein falscher Heal in Szenario 1 landet nicht mehr im Cache und blockiert keine Folge-Szenarien.
- Jeder Locator wird pro Szenario frisch vom LLM geheilt — teurer, aber für Benchmark-Vergleiche fair.

**Benchmark-Rerun mit GLM-4.7-Flash:**

```bash
SPRING_PROFILES_ACTIVE=local-glm-4-7-flash SELF_HEALING_CACHE_ENABLED=false \
  ./gradlew :integration-tests:test
```

> **Hinweis:** Der Cache-Bypass-Rerun mit GLM ist noch nicht ausgeführt — sobald die neuen Zahlen vorliegen, werden sie in den GLM-Abschnitt und in die Direktvergleichs-Matrix eingepflegt.

**Produktive Runs:** Default bleibt `cache.enabled=true`, sonst kostet jeder einzigartige Locator bei jeder Wiederverwendung einen vollen LLM-Call.

---

## LLM-Vergleich

### Ergebnis-Matrix

| Metrik | Anthropic | OpenAI | Mistral | Qwen3-Coder-30B (lokal) | Devstral Small 2 (lokal) | GLM-4.7-Flash (lokal) |
|--------|-----------|--------|---------|-------------------------|--------------------------|------------------------|
| **Szenarien bestanden** | 5/5 (100%) | 5/5 (100%) | 5/5 (100%) | 4/5 (80%) | **5/5 (100%)** | **0/5 (0%)** |
| **Locatoren geheilt** | 8/8 | 8/8 | 8/8 | 7/8 | **8/8** | **1/2 versucht** |
| **Test-Dauer** | 8m 50s | 7m 53s | 7m 52s | 19m 39s | 24m 37s | 9m 13s (Abbruch) |
| **Cache Misses** | 8 | 8 | 8 | 8 | 8 | 2 (dann Cache-Kaskade) |
| **Cache Hits** | 11–16 | 16 | 16 | — | 14 | 14 (alle vom falschen `input_to`) |
| **btn_search Strategie** | `By.id("fab_search")` | `By.id("fab_search")` | `accessibilityId("Suche starten")` | `accessibilityId("Suche starten")` | `accessibilityId("Suche starten")` | nie versucht |
| **Infrastruktur** | Cloud | Cloud | Cloud | Lokal (RTX 3090) | Lokal (RTX 3090) | Lokal (RTX 3090) |
| **Kosten/Lauf** | $$ | $$ | $$ | 0 $ (Strom) | 0 $ (Strom) | 0 $ (Strom) |

### Beobachtungen

1. **Alle 3 Cloud-LLMs und Devstral Small 2 schaffen 100% Healing-Rate** für die 8 Locator-Änderungen. **Qwen3-Coder-30B erreicht 87.5%** — ein respektables Ergebnis, aber mit einer konsistenten Schwäche bei `text_to`. **GLM-4.7-Flash kollabiert auf 0%** — der falsche `input_to`-Heal kippt über den Cache alle Folge-Szenarien.
2. **Mistral, Qwen3-30B und Devstral nutzen AccessibilityId** für den Such-Button statt der Test-Tag-ID — eine valide Alternative, da die App sowohl `testTag("fab_search")` als auch `contentDescription("Suche starten")` hat.
3. **Anthropic** ist etwas langsamer im Gesamtdurchlauf (8m 50s vs. ~7m 52s), wahrscheinlich wegen der ausführlicheren Triage-Analyse.
4. **Cache Hit-Rates** sind bei allen Providern identisch (67%), da der Cache unabhängig vom LLM-Provider arbeitet — was bei korrekten Heals ein Vorteil ist, bei einem falschen Heal aber alle Folge-Szenarien killt (Beweis: GLM-4.7-Flash, Cache-Kaskade nach falschem `input_to`).
5. **Lokale LLMs sind ~2.5–3× langsamer** als die Cloud-Provider — 75–145 s pro Locator-Heal vs. 5–15 s bei Cloud-Modellen. Reasoning-Modelle wie GLM-4.7-Flash addieren noch einmal ~30 % Latenz on top, weil pro Call ein expliziter Chain-of-Thought im `reasoning_content`-Feld erzeugt wird.
6. **Devstral > Qwen3-30B für Locator-Generalisierung:** Devstral schlägt `label_departure`/`label_arrival` vor (symmetrisches Namensmuster), Qwen3 halluziniert asymmetrisch `label_to` und wiederholt den Vorschlag im Retry. Prompt-Guardrail gegen Wiederholung ist weiterhin sinnvoll, auch wenn Devstral den Fehler nicht triggert.
7. **Reasoning-Modelle für Locator-Substitution sind Overkill:** GLM-4.7-Flash investiert ~1.000 Triage-Completion-Tokens und ~2.200 Healer-Completion-Tokens pro Heal (3-4× soviel wie Code-Modelle), liefert aber kein besseres Ergebnis. Für rein syntaktische Tasks bringt die explizite Reasoning-Phase keinen Mehrwert. Für komplexere Stages (Triage-Klassifikation, App-Bug-Berichte) könnte sich das anders darstellen — bisher nicht getestet.

---

## verify-fix.sh Validierung

Das `verify-fix.sh`-Script wurde erfolgreich validiert:

### Optimierung: Infrastruktur-Persistenz

```
┌─────────────────────────────────────────────────┐
│  Vorher (unoptimiert):                          │
│  Emulator Start (3min) → Baseline → Down        │
│  Emulator Start (3min) → Fix-Branch → Down      │
│  Gesamtzeit: ~25 min                            │
├─────────────────────────────────────────────────┤
│  Nachher (optimiert mit --no-deps):             │
│  Emulator Start (3min) → Baseline → Fix-Branch  │
│  Nur test-runner wird neu gebaut                 │
│  Gesamtzeit: ~15 min                            │
└─────────────────────────────────────────────────┘
```

### Validierungs-Ergebnis

```
╔══════════════════════════════════════════════════╗
║  Verification Results                            ║
╠══════════════════════════════════════════════════╣
║  Baseline:  26/26 passed, 0 failed               ║
║  Fix:       26/26 passed, 0 failed               ║
╠══════════════════════════════════════════════════╣
║  Scenario Comparison:                            ║
║    [OK]            Direkte Verbindung finden      ║
║    [OK]            Verbindung mit Umstieg         ║
║    [OK]            Keine Verbindung gefunden      ║
║    [OK]            Einfache ID-Änderung ...       ║
║    [OK]            Verbindungssuche mit Umstieg   ║
╚══════════════════════════════════════════════════╝
```

### Nutzung

```bash
# Fix-Branch gegen master vergleichen (v2 mit Anthropic)
./verify-fix.sh feature/my-fix

# Mit anderem LLM-Provider
./verify-fix.sh feature/my-fix openai
```

**Features:**
- Automatisches `git stash` / `git stash pop` für uncommitted Changes
- Infra (Emulator + Backend + Forwarder) startet nur **einmal** (`--no-deps`)
- Szenario-Vergleich mit Status-Labels: `[FIXED]`, `[REGRESSION]`, `[OK]`, `[STILL FAILING]`
- Reports in `build/reports/verify/` (HTML + JSON)

---

## Cucumber Reports

Nach jedem Test-Lauf werden versionsspezifische Reports erzeugt:

```
build/reports/
├── cucumber-v1.html      # v1 Baseline — 1.98 MB (mit eingebetteten Screenshots)
├── cucumber-v1.json      # v1 Daten — 1.02 MB
├── cucumber-v2.html      # v2 Self-Healing — 1.70 MB (letzter Lauf: Mistral)
├── cucumber-v2.json      # v2 Daten — 747 KB
├── cucumber.html          # Letzter Lauf (identisch mit cucumber-v2)
├── cucumber.json
└── verify/
    ├── cucumber-baseline.html
    ├── cucumber-baseline.json
    ├── cucumber-fix.html
    └── cucumber-fix.json
```

Jeder Report enthält **eingebettete Screenshots** (PNG) für jedes Szenario, die den Endzustand der App nach dem Szenario zeigen.

---

## Highlight-Feature (Rote Markierung)

### Wie es funktioniert

Die App nutzt `healableTestTag()` (statt `Modifier.testTag()`) — ein Custom-Modifier, der bei Healing-Events einen **3dp roten Border** um das geheilte Element zeichnet:

```kotlin
// HealingHighlight.kt
fun Modifier.healableTestTag(tag: String): Modifier = composed {
    val isHighlighted = tag in HealingHighlightState.highlightedTags
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) Color.Red else Color.Transparent
    )
    testTag(tag).then(
        if (isHighlighted) Modifier.border(3.dp, borderColor, RoundedCornerShape(4.dp))
        else Modifier
    )
}
```

**Ablauf:**
1. `SelfHealingAppiumDriver` heilt einen Locator erfolgreich
2. Sendet einen Android Broadcast: `am broadcast -a de.keiss.selfhealing.HIGHLIGHT --es tag fab_search`
3. `HealingHighlightReceiver` empfängt den Broadcast und setzt den roten Border
4. Nach **3 Sekunden** (`HIGHLIGHT_DURATION_MS`) wird der Border automatisch entfernt

### Healing-Screenshots im Cucumber-Report

Seit der Implementierung des Healing-Screenshot-Features werden die **roten Highlight-Borders direkt im Cucumber-Report** angezeigt. Der `SelfHealingAppiumDriver` macht unmittelbar nach jedem erfolgreichen Healing einen Screenshot (500ms Delay für die Compose-Animation) und speichert ihn als `HealingScreenshot`. Im `@After tearDown()` werden diese Screenshots dem Cucumber-Szenario angehängt.

**Implementierung:**

```java
// SelfHealingAppiumDriver.java — nach erfolgreichem Healing:
highlightHealedElement(result.healedLocatorExpression());  // Broadcast → roter Border
captureHealingScreenshot(failedLocator, result.healedLocator()); // 500ms warten → Screenshot

// AppSteps.java — im @After Hook:
for (SelfHealingAppiumDriver.HealingScreenshot hs : testConfig.getDriver().getAndClearHealingScreenshots()) {
    scenario.attach(hs.data(), "image/png", hs.description());
}
```

**Ergebnis im Cucumber-Report (verifiziert am 09.04.2026):**

| Screenshot | Beschreibung | Roter Border sichtbar |
|------------|-------------|----------------------|
| Healing #1 | `Self-Healing: By.id: input_from → By.id: departure_station` | Ja — "Abfahrt"-Feld |
| Healing #2 | `Self-Healing: By.id: input_to → By.id: arrival_station` | Ja — "Abfahrt"-Feld (noch aktiv) + "Ankunft"-Feld |
| Healing #3 | `Self-Healing: By.id: btn_search → By.id: fab_search` | Ja — "Ankunft"-Feld mit rotem Border |

> **Timing:** Der 500ms-Delay nach dem Highlight-Broadcast gibt der Compose-Animation (300ms tween) genug Zeit zum Rendern. Die Screenshots werden innerhalb des 3-Sekunden-Fensters (`HIGHLIGHT_DURATION_MS`) aufgenommen, sodass der rote Border garantiert sichtbar ist.

**Wo die Highlights zusätzlich sichtbar sind:**
- **Im Cucumber HTML-Report** — als eingebettete Screenshots mit beschreibenden Namen
- **Live im noVNC** ([http://localhost:6080](http://localhost:6080)) — rote Borders in Echtzeit während der Testausführung
