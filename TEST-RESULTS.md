# Test-Ergebnisse

> Letzte Ausführung: **15.04.2026** — zweiter lokaler Lauf mit Devstral Small 2 (via LM Studio), 5/5 PASSED

## Inhaltsverzeichnis

- [Zusammenfassung](#zusammenfassung)
- [Test-Runs im Detail](#test-runs-im-detail)
  - [v1 Baseline (Anthropic)](#v1-baseline-anthropic)
  - [v2 Self-Healing (Anthropic)](#v2-self-healing-anthropic)
  - [v2 Self-Healing (OpenAI / GPT-4.1)](#v2-self-healing-openai--gpt-41)
  - [v2 Self-Healing (Mistral / Codestral)](#v2-self-healing-mistral--codestral)
  - [v2 Self-Healing (Lokal / Qwen3-Coder-30B)](#v2-self-healing-lokal--qwen3-coder-30b)
  - [v2 Self-Healing (Lokal / Devstral Small 2)](#v2-self-healing-lokal--devstral-small-2)
- [LLM-Vergleich](#llm-vergleich)
- [verify-fix.sh Validierung](#verify-fixsh-validierung)
- [Cucumber Reports](#cucumber-reports)
- [Highlight-Feature (Rote Markierung)](#highlight-feature-rote-markierung)

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
| `./verify-fix.sh` | Anthropic | Baseline + Fix PASSED | Infra-Optimierung validiert | ~15m |

**Alle 3 Cloud-Provider heilen sämtliche 8 Locator-Änderungen erfolgreich. Devstral Small 2 erreicht als lokales Modell ebenfalls 8/8. Qwen3-Coder-30B heilt 7/8 — `text_to` wird als nicht-existenter `label_to` halluziniert.**

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

## LLM-Vergleich

### Ergebnis-Matrix

| Metrik | Anthropic | OpenAI | Mistral | Qwen3-Coder-30B (lokal) | Devstral Small 2 (lokal) |
|--------|-----------|--------|---------|-------------------------|--------------------------|
| **Szenarien bestanden** | 5/5 (100%) | 5/5 (100%) | 5/5 (100%) | **4/5 (80%)** | **5/5 (100%)** |
| **Locatoren geheilt** | 8/8 | 8/8 | 8/8 | **7/8** | **8/8** |
| **Test-Dauer** | 8m 50s | 7m 53s | 7m 52s | 19m 39s | **24m 37s** |
| **Cache Misses** | 8 | 8 | 8 | 8 | 8 |
| **Cache Hits** | 11–16 | 16 | 16 | — | 14 |
| **btn_search Strategie** | `By.id("fab_search")` | `By.id("fab_search")` | `accessibilityId("Suche starten")` | `accessibilityId("Suche starten")` | `accessibilityId("Suche starten")` |
| **Infrastruktur** | Cloud | Cloud | Cloud | Lokal (RTX 3090) | Lokal (RTX 3090) |
| **Kosten/Lauf** | $$ | $$ | $$ | 0 $ (Strom) | 0 $ (Strom) |

### Beobachtungen

1. **Alle 3 Cloud-LLMs und Devstral Small 2 schaffen 100% Healing-Rate** für die 8 Locator-Änderungen. **Qwen3-Coder-30B erreicht 87.5%** — ein respektables Ergebnis, aber mit einer konsistenten Schwäche bei `text_to`.
2. **Mistral, Qwen3-30B und Devstral nutzen AccessibilityId** für den Such-Button statt der Test-Tag-ID — eine valide Alternative, da die App sowohl `testTag("fab_search")` als auch `contentDescription("Suche starten")` hat.
3. **Anthropic** ist etwas langsamer im Gesamtdurchlauf (8m 50s vs. ~7m 52s), wahrscheinlich wegen der ausführlicheren Triage-Analyse.
4. **Cache Hit-Rates** sind bei allen Providern identisch (67%), da der Cache unabhängig vom LLM-Provider arbeitet.
5. **Lokale LLMs sind ~2.5–3× langsamer** als die Cloud-Provider — 75–115 s pro Locator-Heal vs. 5–15 s bei Cloud-Modellen. Devstral ist pro Call langsamer als Qwen3, vermeidet aber Retry-Loops.
6. **Devstral > Qwen3-30B für Locator-Generalisierung:** Devstral schlägt `label_departure`/`label_arrival` vor (symmetrisches Namensmuster), Qwen3 halluziniert asymmetrisch `label_to` und wiederholt den Vorschlag im Retry. Prompt-Guardrail gegen Wiederholung ist weiterhin sinnvoll, auch wenn Devstral den Fehler nicht triggert.

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
