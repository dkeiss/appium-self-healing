# Test-Ergebnisse

> Letzte Ausführung: **20.04.2026** — Qwen3-Coder-30B & GLM-4.7-Flash lokal: jeweils 6/6 nach max-page-source-chars=0 Fix (kein XML-Truncating)

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
| `./run-tests-podman.sh v1` | Anthropic | 6/6 PASSED | Kein Healing nötig | ~3m |
| `./run-tests-podman.sh v2` | Anthropic (Claude Sonnet) | 6/6 PASSED | 10 Locator geheilt | ~15m |
| `./run-tests-podman.sh v2 openai` | OpenAI (GPT-4.1) | 6/6 PASSED | 10 Locator geheilt | ~10m |
| `./run-tests-podman.sh v2 mistral` | Mistral (Codestral) | 6/6 PASSED | 10 Locator geheilt | ~10m |
| `./run-tests-podman.sh v2 local-qwen3-30b` | LM Studio (Qwen3-Coder-30B) | **6/6 PASSED** | 10/10 Locator geheilt | ~22m 19s |
| `./run-tests-podman.sh v2 local-devstral` | LM Studio (Devstral Small 2) | 6/6 PASSED | 10/10 Locator geheilt | ~70m |
| `./run-tests-podman.sh v2 local-glm-4-7-flash` | LM Studio (GLM-4.7-Flash) | **6/6 PASSED** | 10/10 Locator geheilt | ~51m 39s |
| `./verify-fix.sh` | Anthropic | Baseline + Fix PASSED | Infra-Optimierung validiert | ~15m |

**Alle Provider — Cloud und lokal — heilen sämtliche Locator-Änderungen erfolgreich nach dem `max-page-source-chars=0`-Fix. Qwen3-Coder-30B und GLM-4.7-Flash benötigen das vollständige XML (kein Truncating), da `journey_card` im hinteren Teil der Hierarchie liegt.**

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
║  Dauer: 22m 19s                                  ║
║  Lauf: 2026-04-20 (max-page-source-chars=0)      ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis | Dauer |
|---|----------|----------|-------|
| 1 | Direkte Verbindung finden | PASSED | — |
| 2 | Verbindung mit Umstieg | PASSED | — |
| 3 | Keine Verbindung gefunden | PASSED | — |
| 4 | Einfache ID-Änderung wird geheilt | PASSED | — |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | PASSED | — |
| 6 | Fahrplan-Details hinter BottomSheet-Navigation | PASSED | — |

**Geheilte Locatoren (10/10 korrekt):**

| v1-Locator | Vorschlag Qwen3-30B | Strategie | Versuche | Ergebnis |
|------------|---------------------|-----------|---------|----------|
| `input_from` | `departure_station` | `By.id` | 1 | ✅ |
| `input_to` | `arrival_station` | `By.id` | 1 | ✅ |
| `btn_search` | `fab_search` | `By.id` | 1 | ✅ |
| `connection_item` | `journey_card` | `By.id` | 1 | ✅ |
| `text_from` | `Berlin Hbf` | `accessibilityId` | 1 | ✅ |
| `text_to` | `label_arrival` | `By.id` | 2 (nach Ablehnung `label_to`) | ✅ |
| `text_transfers` | `label_changes` | `By.id` | 1 | ✅ |
| `text_no_results` | — | — | — | (Cache) |
| `leg_train_number` | `Zug ICE ICE 123` | `accessibilityId` | 1 | ✅ |
| `leg_platform` | `Gleis 9` | `accessibilityId` | 1 | ✅ |

**Token- & Latenz-Metriken pro Heal-Call:**

| Phase | Prompt Tokens | Completion Tokens | Total | Latenz |
|-------|---------------|-------------------|-------|--------|
| Triage | ~2.254 | ~100–125 | ~2.354–2.379 | ~3 s |
| LocatorHealer | ~5.400–8.500 | ~570–750 | ~5.960–9.200 | ~80–100 s |
| **Gesamt pro Locator** | **~7.600–10.800** | **~670–870** | **~8.300–11.600** | **~80–100 s** |

Triage-Confidence war bei allen Fällen konstant **0.95** mit Kategorie `LOCATOR_CHANGED`.

**Wichtig — `max-page-source-chars=0`:** Der `connection_item`-Locator (`journey_card`) liegt im hinteren Teil der XML-Hierarchie. Mit dem früheren Default-Truncating (15 000 Zeichen) war `journey_card` abgeschnitten → Halluzination. Mit `max-page-source-chars: 0` (kein Truncating) findet Qwen3 es beim ersten Versuch. Einziger verbliebener Fehltreffer: `text_to → label_to` (Attempt 1), korrigiert zu `label_arrival` nach Rejection (Attempt 2).

**Historischer Lauf 2026-04-15 (vor Fix, n_ctx=4096):** 4/5 Szenarien, `text_to` halluziniert als `label_to`, Gesamtdauer 19m 39s.

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

> Vierter lokaler Lauf — Z.ai's GLM-4.7-Flash, ein **Reasoning-Modell** (separates `reasoning_content`-Feld in der API-Response), via LM Studio auf RTX 3090.

```
╔══════════════════════════════════════════════════╗
║  App Version: v2                                 ║
║  LLM Provider: local-glm-4-7-flash (LM Studio)   ║
║  Modell: zai-org/glm-4.7-flash (Reasoning)       ║
║  Backend: OpenAI-kompatible REST-API             ║
║  Dauer: 51m 39s                                  ║
║  Lauf: 2026-04-20 (max-page-source-chars=0)      ║
╚══════════════════════════════════════════════════╝
```

| # | Szenario | Ergebnis |
|---|----------|----------|
| 1 | Direkte Verbindung finden | PASSED |
| 2 | Verbindung mit Umstieg | PASSED |
| 3 | Keine Verbindung gefunden | PASSED |
| 4 | Einfache ID-Änderung wird geheilt | PASSED |
| 5 | Verbindungssuche mit Umstieg nach UI-Redesign | PASSED |
| 6 | Fahrplan-Details hinter BottomSheet-Navigation | PASSED |

**Geheilte Locatoren (10/10 korrekt, alle beim ersten Versuch):**

| v1-Locator | Vorschlag GLM-4.7-Flash | Strategie | Latenz | Ergebnis |
|------------|--------------------------|-----------|--------|----------|
| `input_from` | `departure_station` | `By.id` | ~8 min | ✅ |
| `input_to` | `arrival_station` | `By.id` | ~4 min | ✅ |
| `btn_search` | `fab_search` | `By.id` | ~4 min | ✅ |
| `connection_item` | `journey_card` | `By.id` | ~6 min | ✅ |
| `text_from` | `label_departure` | `By.id` | ~6 min | ✅ |
| `text_to` | `label_arrival` | `By.id` | ~4 min | ✅ |
| `text_transfers` | `label_changes` | `By.id` | ~4 min | ✅ |
| `text_no_results` | `empty_state_text` | `By.id` | ~4 min | ✅ |
| `leg_train_number` | `Zug ICE ICE 123` | `accessibilityId` | ~3 min | ✅ |
| `leg_platform` | `Gleis 9` | `accessibilityId` | ~6 min | ✅ |

**Token- & Latenz-Metriken pro Heal-Call:**

| Phase | Prompt Tokens | Completion Tokens | Total | Latenz |
|-------|---------------|-------------------|-------|--------|
| Triage | ~2.254 | ~100–120 | ~2.354–2.374 | ~2–3 s |
| LocatorHealer | ~4.440–8.077 | **~1.468–3.020** | ~5.428–10.349 | ~3–6 min |
| **Gesamt pro Locator** | **~6.700–10.300** | **~1.600–3.100** | **~7.800–13.400** | **~3.5–8 min** |

> **Reasoning-Token-Overhead:** Die Completion-Tokens sind 2-4× höher als bei Qwen3/Devstral, weil GLM-4.7-Flash intern eine Chain-of-Thought im `reasoning_content`-Feld erzeugt. `max-tokens: 16384` ist Pflicht, sonst läuft der Reasoning-Block aus und `content` ist leer.

**Anpassungen im Profil (`local-glm-4-7-flash`):**

```yaml
chat:
  options:
    model: zai-org/glm-4.7-flash
    temperature: 0.1
    max-tokens: 16384  # Reasoning-Modell braucht Budget für reasoning_content + content

self-healing:
  prompt:
    max-page-source-chars: 0  # Kein Truncating — journey_card liegt im hinteren XML-Teil
```

**Historischer Lauf 2026-04-16 (vor Fix, n_ctx=4096, Truncating aktiv):** 0/5 Szenarien — `input_to` als Label `Ankunftsbahnhof` halluziniert (statt Input-Feld), Cache-Kaskade kippte alle Folge-Szenarien.

---

## Lokale LLMs im Direktvergleich

| Metrik | Qwen3-Coder-30B | Devstral Small 2 | GLM-4.7-Flash |
|--------|----------------|------------------|---------------|
| **Modelltyp** | Code-Modell | Code-Modell | **Reasoning-Modell** |
| **Modell-ID (LM Studio)** | `qwen/qwen3-coder-30b` | `mistralai/devstral-small-2-2512` | `zai-org/glm-4.7-flash` |
| **Szenarien bestanden** | **6/6 (100 %)** | **6/6 (100 %)** | **6/6 (100 %)** |
| **Locatoren geheilt** | 10/10 (1× Attempt 2) | **10/10** | **10/10** |
| **Test-Dauer** | 22m 19s | ~70m | 51m 39s |
| **Latenz/Heal** | ~80–100 s | ~110 s | ~3.5–8 min |
| **Triage prompt/completion** | ~2.254 / ~100 | ~2.700 / ~85 | ~2.254 / **~100–120** |
| **Healer prompt/completion** | ~5.400–8.500 / ~600 | ~5.300 / ~700 | ~4.440–8.077 / **~1.500–3.020** |
| **Triage Confidence (typ.)** | 0.95 | 0.95 | 0.95 |
| **Halluzinationen** | `text_to → label_to` (1×, Attempt 2 korrigiert) | keine | keine |
| **max-page-source-chars** | 0 (kein Truncating) | Default (15 000) | 0 (kein Truncating) |
| **Hardware** | RTX 3090 (24 GB) | RTX 3090 (24 GB) | RTX 3090 (24 GB) |

**Zusammenfassung der drei lokalen Modelle (Stand 2026-04-20):**

1. **Devstral Small 2** ist nach wie vor der zuverlässigste: keine Halluzination, keine Retries, sauber generalisierende Locatoren (`label_departure`/`label_arrival`). Benötigt kein XML-Truncating-Fix.
2. **Qwen3-Coder-30B** ist die Speed-Option: ~22 min Gesamtlaufzeit, alle 10 Locatoren geheilt. Einzige Schwäche: `text_to → label_to` im ersten Versuch (bekanntes Muster), durch Rejection-Liste im zweiten Versuch korrigiert.
3. **GLM-4.7-Flash** erreicht ebenfalls 6/6 — ohne eine einzige Halluzination. Nachteil: ~3–8 Min/Heal (Reasoning-Overhead), Gesamtlaufzeit 51 min. Für Umgebungen ohne RTX 3090 unpraktisch, aber qualitativ überraschend stark.

**Entscheidender Fix für Qwen3 und GLM:** `max-page-source-chars: 0` in den lokalen Profilen. Der `journey_card`-Locator liegt im hinteren Teil der XML-Hierarchie — mit dem früheren Truncating (15 000 Zeichen) war er abgeschnitten und beide Modelle halluzinierten.

**Empfehlung für lokale Setups:** Devstral Small 2 als Default, Qwen3-Coder-30B wenn Gesamtlaufzeit wichtiger als Zero-Retry-Garantie ist. GLM-4.7-Flash wenn Reasoning-Qualität gewünscht und Latenz toleriert wird.

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
  ./run-tests-podman.sh v2 local-glm-4-7-flash
```

> **Status:** Der kombinierte Fix (`max-page-source-chars=0` + großes n_ctx) hat das ursprüngliche GLM-Problem (falscher `input_to`-Heal) behoben. Mit dem 2026-04-20-Lauf sind alle 10 Locatoren korrekt geheilt (6/6 Szenarien). Cache-Bypass ist für faire Einzel-Locator-Benchmarks weiterhin nützlich, aber für den Funktionsnachweis nicht mehr erforderlich.

**Produktive Runs:** Default bleibt `cache.enabled=true`, sonst kostet jeder einzigartige Locator bei jeder Wiederverwendung einen vollen LLM-Call.

---

## LLM-Vergleich

### Ergebnis-Matrix

| Metrik | Anthropic | OpenAI | Mistral | Qwen3-Coder-30B (lokal) | Devstral Small 2 (lokal) | GLM-4.7-Flash (lokal) |
|--------|-----------|--------|---------|-------------------------|--------------------------|------------------------|
| **Szenarien bestanden** | 6/6 (100%) | 6/6 (100%) | 6/6 (100%) | **6/6 (100%)** | **6/6 (100%)** | **6/6 (100%)** |
| **Locatoren geheilt** | 10/10 | 10/10 | 10/10 | 10/10 (1× Attempt 2) | **10/10** | **10/10** |
| **Test-Dauer** | ~15m | ~10m | ~10m | 22m 19s | ~70m | 51m 39s |
| **Latenz/Heal** | ~12 s | ~5 s | ~4 s | ~80–100 s | ~110 s | ~3.5–8 min |
| **btn_search Strategie** | `By.id("fab_search")` | `By.id("fab_search")` | `accessibilityId("Suche starten")` | `By.id("fab_search")` | `accessibilityId("Suche starten")` | `By.id("fab_search")` |
| **Infrastruktur** | Cloud | Cloud | Cloud | Lokal (RTX 3090) | Lokal (RTX 3090) | Lokal (RTX 3090) |
| **Kosten/Lauf** | $$ | $$ | $$ | 0 $ (Strom) | 0 $ (Strom) | 0 $ (Strom) |

### Beobachtungen

1. **Alle 6 Provider schaffen 100% Healing-Rate** für alle 10 Locator-Änderungen. Der entscheidende Fix: `max-page-source-chars=0` für lokale Profile (kein XML-Truncating) — `journey_card` lag im hinteren Teil der Hierarchie.
2. **Mistral und Devstral nutzen AccessibilityId** für den Such-Button (`Suche starten`) statt der Test-Tag-ID — valide Alternative, da die App beides hat.
3. **Lokale LLMs sind ~5–90× langsamer** als Cloud-Provider (80 s bis 8 min pro Heal vs. 4–12 s), aber kostenlos. Für CI-Pipelines empfehlen sich Cloud-Provider; für Privacy-sensible oder Offline-Szenarien reicht Qwen3-30B (~22 min gesamt).
4. **GLM-4.7-Flash überraschend stark:** Keine einzige Halluzination im 2026-04-20-Lauf — besser als Qwen3 (`text_to → label_to` benötigt 1 Retry). Dafür 2–4× mehr Completion-Tokens durch Reasoning-Overhead.
5. **Devstral als Null-Retry-Garantie:** Einziges Modell ohne jede Halluzination über alle Runs hinweg. `label_departure`/`label_arrival` direkt korrekt (Qwen3 halluziniert `label_to` im ersten Versuch).
6. **Cache-Kaskade (historisch):** Der frühere GLM-0/5-Lauf (2026-04-16) wurde durch einen falschen `input_to`-Heal ausgelöst, der sich per Cache in alle Szenarien propagierte. Mit dem `max-page-source-chars=0`-Fix tritt dieser Fall nicht mehr auf.

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
