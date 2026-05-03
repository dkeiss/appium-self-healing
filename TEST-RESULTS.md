# Test-Ergebnisse

> Letzte Ausführung: **02.05.2026** — alle vier neu vermessenen Provider (Anthropic, OpenAI, Mistral, lokal Devstral) heilen 6/6 Szenarien mit aktivem Cache und 10 unique Heals.

## Inhaltsverzeichnis

- [Zusammenfassung](#zusammenfassung)
- [MCP vs. no-MCP Benchmark](#mcp-vs-no-mcp-benchmark)
- [v1 Baseline](#v1-baseline)
- [Lokale LLMs im Detail](#lokale-llms-im-detail)
  - [Qwen3-Coder-30B](#qwen3-coder-30b)
  - [Devstral Small 2](#devstral-small-2)
  - [GLM-4.7-Flash](#glm-47-flash)
- [LLM-Vergleich (Cloud + lokal)](#llm-vergleich-cloud--lokal)
- [Cache-Bypass für LLM-Benchmarks](#cache-bypass-für-llm-benchmarks)
- [Vision-affiner Toolbar-Track](#vision-affiner-toolbar-track)
- [verify-fix.sh Validierung](#verify-fixsh-validierung)

---

## Zusammenfassung

App v2 · 6 Szenarien inkl. `@very-hard-navigation` · Default-Konfiguration (Cache aktiv).

| Provider | Modell | Tests | Unique Heals | ∅ Heal-Zeit | Gesamtdauer | Lauf | Infrastruktur |
|---|---|---|---|---|---|---|---|
| Anthropic | claude-sonnet-4-6 | **6/6** | 10 (Attempt 1: 10) | 11 385 ms | 9 min 30 s | 2026-05-02 | Cloud |
| OpenAI | gpt-4.1 | **6/6** | 10 (Attempt 1: 10) | 5 532 ms | 8 min 9 s | 2026-05-02 | Cloud |
| Mistral | codestral-latest | **6/6** | 10 (Attempt 2: 1× `leg_platform`) | 3 586 ms | 8 min 12 s | 2026-05-02 | Cloud |
| Devstral Small 2 | mistralai/devstral-small-2-2512 | **6/6** | 10 (Attempt 1: 10) | 113 102 ms | 29 min 57 s | 2026-05-02 | Lokal (RTX 3090) |
| Qwen3-Coder-30B | qwen/qwen3-coder-30b | **6/6** | 10 (Attempt 2: 1× `text_to`) | ~80–100 s | 22 min 19 s | 2026-04-20 | Lokal (RTX 3090) |
| GLM-4.7-Flash | zai-org/glm-4.7-flash | **6/6** | 10 (Attempt 1: 10) | ~3.5–8 min | 51 min 39 s | 2026-04-20 | Lokal (RTX 3090) |

> **v1-Baseline (alle Provider äquivalent — kein Healing):** `./scripts/run-tests-podman.sh v1 anthropic` → 6/6 PASSED, 0 Heals, test-runner BUILD in 1 min 44 s. Bestätigt mit Anthropic stellvertretend; auf v1 wird kein LLM aufgerufen.

**Kernergebnis:** Alle sechs Provider heilen sämtliche Locator-Änderungen erfolgreich. Mistral hat reproduzierbar einen `leg_platform → leg_item_0_platform`-Fehlversuch in Attempt 1, der durch `rejectedLocators` in Attempt 2 zu `accessibilityId: Gleis 9` korrigiert wird. Devstral wählt für `btn_search` konsistent `accessibilityId("Suche starten")` statt `By.id("fab_search")` (valide Alternative).

> **Cache-disabled Vergleich (`@very-hard-navigation`-Track mit `SELF_HEALING_CACHE_ENABLED=false`):** Detail-Zahlen pro Run-Cluster siehe [docs/mcp-comparison-report.md](docs/mcp-comparison-report.md). Dort sind 31 Heals/Run statt 10, da jeder Locator pro Szenario neu vom LLM bestimmt wird.

---

## MCP vs. no-MCP Benchmark

Detailbericht: [docs/mcp-comparison-report.md](docs/mcp-comparison-report.md). Kurzfassung:

| Provider | MCP | Tests | Gesamtdauer | Heals | ∅ Heal-Zeit | Tokens |
|---|---|---|---|---|---|---|
| Anthropic | ✗ | 6/6 | 14 min 48 s | 31 | 12 085 ms | 313 133 |
| Anthropic | ✓ | 6/6 | 23 min 40 s | 31 | 15 656 ms | 332 491 |
| OpenAI | ✗ | 6/6 | 9 min 54 s | 31 | 5 265 ms | 254 565 |
| OpenAI | ✓ | 6/6 | 23 min 11 s | 31 | 20 400 ms | 256 917 |
| Mistral | ✗ | 6/6 | 9 min 27 s | 36 | 3 775 ms | 301 450 |
| Mistral | ✓ | 6/6 | 9 min 37 s | 31 | 4 079 ms | 287 653 |

**Fazit:** MCP liefert keinen Mehrwert solange `appium-mcp` keine bestehende Appium-Session sharen kann (alle Tool-Calls geben `No driver found` zurück). MCP bleibt deaktiviert (`self-healing.mcp.enabled=false`).

### rejected-locators Fix

`FailureContext` trackt fehlgeschlagene Heal-Vorschläge über Retries. Mistral erfand bei Attempt 1 `leg_item_0_platform`; Attempt 2+ sieht den Vorschlag als _"Already tried — NOT found"_ und Mistral wechselt auf `accessibilityId: Gleis 9`. Mistral ist seitdem **6/6 ohne MCP** (vorher 5/6). Dasselbe Muster behebt Anthropic-Halluzinationen (`contentDescriptionStartsWith` etc.) — gefangen über `InvalidSelectorException` in `SelfHealingAppiumDriver.attemptHealAndRetry`.

---

## v1 Baseline

`./scripts/run-tests-podman.sh v1 anthropic` (2026-05-02, BUILD 1 min 44 s): **6/6 PASSED**, `Total healings: 0 · Cache hits: 0 · Cache misses: 0`. Bestätigt, dass Self-Healing keine Regression auf der unveränderten v1-App verursacht. Da auf v1 kein LLM aufgerufen wird, ist das Ergebnis providerunabhängig — eine Verifikation pro Anthropic genügt.

---

## Lokale LLMs im Detail

Alle drei Modelle liefen über LM Studio auf Windows 11 mit RTX 3090 (24 GB VRAM), OpenAI-kompatibles REST-Backend.

### Setup pro Modell

| Modell | Profil | Modell-ID | Dauer | Lauf | Track |
|--------|--------|-----------|-------|------|-------|
| Qwen3-Coder-30B (18.63 GB MoE) | `local-qwen3-30b` | `qwen/qwen3-coder-30b` | 22 min 19 s | 2026-04-20 | 6 Szenarien |
| Devstral Small 2 | `local-devstral` | `mistralai/devstral-small-2-2512` | 29 min 57 s | 2026-05-02 | 6 Szenarien (10 Heals, Cache aktiv) |
| GLM-4.7-Flash (Reasoning) | `local-glm-4-7-flash` | `zai-org/glm-4.7-flash` | 51 min 39 s | 2026-04-20 | 6 Szenarien |

### Token- & Latenz-Metriken pro Heal-Call

| Modell | Triage Prompt/Completion | Healer Prompt/Completion | Pro Locator Total | Latenz |
|--------|--------------------------|--------------------------|-------------------|--------|
| Qwen3-Coder-30B | ~2 254 / ~100–125 | ~5 400–8 500 / ~570–750 | ~8 300–11 600 | ~80–100 s |
| Devstral Small 2 | ~2 700 / ~85 | ~5 300 / ~700 | ~7 900–9 700 | ~97–126 s |
| GLM-4.7-Flash | ~2 254 / ~100–120 | ~4 440–8 077 / **~1 468–3 020** | ~7 800–13 400 | **~3.5–8 min** |

Triage-Confidence konstant **0.95** mit Kategorie `LOCATOR_CHANGED` über alle drei Modelle.

### Heal-Verhalten pro Modell

**Qwen3-Coder-30B** — 10/10 Locatoren geheilt (alle in Attempt 1 außer `text_to`: Attempt 1 `label_to` halluziniert, Attempt 2 nach Rejection korrekt zu `label_arrival`). `text_from` als textbasierte Accessibility-ID `Berlin Hbf` (fragil gegenüber Testdaten). Voraussetzung: `max-page-source-chars: 0`.

**Devstral Small 2** — 10/10 Heals beim ersten Versuch (cache-aktiver Lauf 2026-05-02, 29 min 57 s; cache-disabled Vergleichslauf 2026-04-17 lieferte 31/31 in 70 min 9 s, siehe [docs/mcp-comparison-report.md](docs/mcp-comparison-report.md)). Keine Halluzination, kein Retry. `leg_platform` direkt korrekt als `accessibilityId: Gleis 9` (Cloud-Mistral braucht dafür den `rejectedLocators`-Fix und zwei Attempts). Benötigt **kein** `max-page-source-chars=0`. Wählt für `text_from`/`text_to` die saubersten Generalisierungen `label_departure`/`label_arrival`, für `btn_search` konsistent `accessibilityId("Suche starten")`.

**GLM-4.7-Flash** — 10/10 Locatoren beim ersten Versuch, keine Halluzination. Reasoning-Modell mit separatem `reasoning_content`-Feld → Completion-Tokens 2–4× höher als Qwen3/Devstral. `max-tokens: 16384` ist Pflicht, sonst läuft der Reasoning-Block aus und `content` ist leer. Voraussetzung: `max-page-source-chars: 0`.

```yaml
# Profil-Anpassungen (local-glm-4-7-flash)
chat:
  options:
    model: zai-org/glm-4.7-flash
    temperature: 0.1
    max-tokens: 16384
self-healing:
  prompt:
    max-page-source-chars: 0
```

---

## LLM-Vergleich (Cloud + lokal)

| Metrik | Anthropic | OpenAI | Mistral | Qwen3-Coder-30B | Devstral Small 2 | GLM-4.7-Flash |
|--------|-----------|--------|---------|-----------------|------------------|---------------|
| Modelltyp | Cloud | Cloud | Cloud | Code-Modell | Code-Modell | **Reasoning-Modell** |
| Szenarien | 6/6 | 6/6 | 6/6 | 6/6 | 6/6 | 6/6 |
| Unique Heals | 10 (Attempt 1) | 10 (Attempt 1) | 10 (1× Attempt 2) | 10 (1× Attempt 2) | 10 (Attempt 1) | 10 (Attempt 1) |
| Test-Dauer | 9 min 30 s | 8 min 9 s | 8 min 12 s | 22 min 19 s | 29 min 57 s | 51 min 39 s |
| ∅ Latenz/Heal | 11 385 ms | 5 532 ms | 3 586 ms | ~80–100 s | 113 102 ms | ~3.5–8 min |
| `btn_search`-Strategie | `id(fab_search)` | `id(fab_search)` | `id(fab_search)` | `id(fab_search)` | `accessibilityId(Suche starten)` | `id(fab_search)` |
| `max-page-source-chars` | Default | Default | Default | **0** | Default | **0** |
| Kosten/Lauf | ~$1.50 | ~$0.82 | ~$0.12 | 0 (Strom) | 0 (Strom) | 0 (Strom) |

### Beobachtungen

1. **100 % Healing-Rate über alle sechs Provider.** Voraussetzung für Qwen3 + GLM: `max-page-source-chars=0` — `journey_card` lag im hinteren XML-Teil.
2. **Mistral & Devstral** wählen `accessibilityId("Suche starten")` für `btn_search` statt `By.id("fab_search")` — valide Alternative, beide Strategien funktionieren.
3. **Lokale LLMs ~5–90× langsamer** als Cloud, aber kostenlos. CI-Pipelines: Cloud bevorzugt; Privacy/Offline: Qwen3-30B bietet bestes Latenz/Qualitäts-Verhältnis.
4. **GLM-4.7-Flash** liefert die saubersten Heals (keine Halluzination), bei höchster Latenz wegen Reasoning-Overhead.
5. **Devstral** als Null-Retry-Garantie — einziges Modell ohne Halluzination über alle Runs hinweg.

**Empfehlung für lokale Setups:** Devstral als Default; Qwen3-30B wenn Gesamtlaufzeit wichtiger als Zero-Retry; GLM-4.7-Flash wenn Reasoning-Qualität gewünscht und Latenz toleriert wird.

---

## Cache-Bypass für LLM-Benchmarks

Per ENV `SELF_HEALING_CACHE_ENABLED=false` lässt sich der `PromptCache` deaktivieren — nützlich für faire Einzel-Locator-Benchmarks, bei denen ein falscher Heal in Szenario 1 nicht via Cache in Folge-Szenarien propagieren soll. Default bleibt `true`.

---

## Vision-affiner Toolbar-Track

Mit dem Feature [toolbar_actions.feature](integration-tests/src/test/resources/features/toolbar_actions.feature) (Tag `@toolbar`) gibt es eine eigene Strecke, die XML-only-Heal eigentlich aushebeln soll: Die drei Toolbar-Aktionen (Filtern / Sortieren / Teilen) teilen sich in v2 denselben `testTag` (`toolbar_action`) und dieselbe `content-description` (`Aktion`). Nur das gerenderte Icon-Glyph unterscheidet sie. Verifikation läuft über `toolbar_status` — ein falscher Heal kippt auf der Assertion, nicht auf `NoSuchElementException`.

### Iteration 1 — semantische Locator-Namen (`btn_filter`/`btn_sort`/`btn_share`)

| Profil | Tests | Toolbar-Heals | Gewählte Locatoren | ∅ Heal-Latenz | Gesamtdauer |
|---|---|---|---|---|---|
| `anthropic-vision` (Vision) | **9/9** | 3/3 Attempt 1 | `instance(0)` / `instance(1)` / `instance(2)` ✓ | 21.4 s | 15 min 11 s |
| `anthropic` (Text-only) | **9/9** | 3/3 Attempt 1 | `instance(0)` / `instance(1)` / `instance(2)` ✓ | 16.3 s | 13 min 48 s |

**Befund:** Sonnet 4.6 trifft auch ohne Vision identisch — die Variable-Namen (`btn_filter`/`btn_sort`/`btn_share`) plus die UI-Konvention Filter→Sort→Share von links nach rechts geben einen so starken Prior, dass das Modell die Position auch ohne Bild korrekt rät. Track diskriminiert nicht.

### Iteration 2 — semantisch entkoppelte aber alphabetische Locator-Namen (`btn_action_a/b/c`)

Locatoren auf `btn_action_a/b/c` umbenannt, Page-Object-Methoden auf `tapActionA/B/C()`, Cucumber-Steps auf "ich auf Aktion A/B/C klicke". Sichtbare Button-Beschriftungen v1 bleiben "Filtern"/"Sortieren"/"Teilen" (für die App-UX), tragen aber keine Heal-relevante Information mehr.

| Profil | Tests | Toolbar-Heals | Gewählte Locatoren | ∅ Heal-Latenz | Gesamtdauer |
|---|---|---|---|---|---|
| `anthropic` (Text-only) | **9/9** | 3/3 Attempt 1 | `instance(0)` / `instance(1)` / `instance(2)` ✓ | 16.1 s | 13 min 54 s |
| `local-devstral` (Text-only) | **7/9** | 1/3 — A korrekt, **B+C falsch** | alle drei → `accessibilityId: Aktion` ⇒ immer instance(0) = Filter | 119.2 s | 35 min 21 s |

**Devstral-Detail:**

| Locator | Heal | Resultat | Erwarteter Status | Gemessen |
|---|---|---|---|---|
| `btn_action_a` | `accessibilityId: Aktion` | klickt instance(0) | "Verbindungen gefiltert" | "Verbindungen gefiltert" ✓ |
| `btn_action_b` | `accessibilityId: Aktion` | klickt instance(0) | "Verbindungen sortiert" | "Verbindungen gefiltert" ✗ |
| `btn_action_c` | `accessibilityId: Aktion` | klickt instance(0) | "Verbindungen geteilt" | "Verbindungen gefiltert" ✗ |

**Befund:**
- **Sonnet bleibt 3/3** — der alphabetische Suffix `_a/_b/_c` plus Layout-Reihenfolge im XML reicht ihm immer noch, um die Position korrekt zu rekonstruieren.
- **Devstral kollabiert auf 1/3** — wählt für jede der drei broken-Locators denselben Locator (`accessibilityId: Aktion`), der den ersten der drei identischen `toolbar_action`-Knoten findet. Aktion A passiert nur zufällig, weil Position 0 = Filter. B und C scheitern auf der `toolbar_status`-Assertion. Damit ist die Vision-Lücke für lokale/schwächere Modelle dokumentiert.
- **Vergleich mit lokalem Vision-Modell steht aus** — Devstral selbst ist text-only. Für eine echte Vision-vs-Text-Lokal-Demo müsste ein Vision-fähiges lokales Modell geladen werden (z. B. Qwen3-VL).

### Iteration 3 — unalphabetisches Rauschen (`btn_m3n` / `btn_x7q` / `btn_p2k`)

Suffixe auf deterministisches Rauschen ohne alphabetische Sortier-Beziehung umbenannt: `m3n` (Filter, Position 0), `x7q` (Sort, Position 1), `p2k` (Share, Position 2). Damit ist auch der `_a/_b/_c → 0/1/2`-Hint der Iteration 2 entfernt. Page-Object-Methoden / Steps / Feature ziehen mit (`tapM3n` / "ich auf den Button m3n klicke" / "Button m3n löst Filter-Verhalten aus").

| Profil | Tests | Toolbar-Heals | Gewählte Locatoren | ∅ Heal-Latenz | Gesamtdauer |
|---|---|---|---|---|---|
| `anthropic` (Text-only) | **9/9** | 3/3 Attempt 1 | `instance(0)` / `instance(1)` / `instance(2)` ✓ | 15.5 s | 13 min 50 s |
| `local-devstral` (Text-only) | **7/9** | 1/3 — m3n korrekt, **x7q+p2k falsch** | alle drei → `accessibilityId: Aktion` ⇒ immer instance(0) = Filter | 119.6 s | 35 min 12 s |

**Befund:**
- **Sonnet bleibt 3/3.** Suffixe `m3n` / `x7q` / `p2k` haben keine alphabetische Position-Beziehung mehr (alphabetisch wäre `m3n < p2k < x7q`, layout-Position ist `m3n=0, x7q=1, p2k=2`). Dass Sonnet trotzdem korrekt mappt, deutet auf einen weiteren Leak: die **Deklarations-Reihenfolge der `BTN_*`-Konstanten in `ResultPage.java`** — der `SourceCodeResolver` lädt das Page-Object beim Heal mit, das Modell sieht die Liste in Layout-Reihenfolge und mappt declaration-index → instance-index. Um Sonnet auf dieser Strecke zu brechen, müsste man die Deklarations-Reihenfolge im Page-Object ebenfalls schütteln (z. B. p2k, m3n, x7q deklarieren) oder die v2-Layout-Reihenfolge gegenüber v1 randomisieren.
- **Devstral kollabiert wieder auf 1/3.** Identisches Muster wie Iteration 2: alle drei broken-Locators mappen auf `accessibilityId: Aktion`, was deterministisch instance(0) = Filter trifft. m3n passt zufällig, x7q (erwartet "sortiert") und p2k (erwartet "geteilt") scheitern auf der `toolbar_status`-Assertion. Devstral ist gegen Locator-Kollisionen wehrlos — egal wie der Suffix heißt.

### Iteration 4 — permutierte Page-Object-Deklaration (`p2k, m3n, x7q`)

`BTN_*`-Konstanten und `tap*()`-Methoden in [ResultPage.java](integration-tests/src/test/java/de/keiss/selfhealing/tests/pages/ResultPage.java) gegen die v2-Layout-Position permutiert: Deklarations-Reihenfolge `p2k → m3n → x7q`, aber gebundene Layout-Positionen weiterhin `m3n=0, x7q=1, p2k=2`. Damit ist der vermutete Source-Code-Order-Leak geschlossen.

| Profil | Tests | Toolbar-Heals | Gewählte Locatoren | ∅ Heal-Latenz | Gesamtdauer |
|---|---|---|---|---|---|
| `anthropic` (Sonnet 4.6, text-only) | **9/9** | **3/3 Attempt 1** ✓ | `instance(0)` / `instance(1)` / `instance(2)` | 15.3 s | 14 min 11 s |
| `openai` (gpt-5.4-mini, text-only) | **7/9** | 1/3 — m3n zufällig korrekt, x7q+p2k falsch | alle drei → `By.id: toolbar_action` ⇒ instance(0) | 5.6 s | 11 min 31 s |
| `openai-vision` (gpt-5.4-mini + Screenshot) | **7/9** | 1/3 — m3n zufällig korrekt, x7q+p2k falsch | alle drei → `accessibilityId: Aktion` ⇒ instance(0) | 5.9 s | 11 min 21 s |
| `mistral` (codestral-latest) | **7/9** | 1/3 — m3n zufällig korrekt, x7q+p2k falsch | alle drei → `By.id: toolbar_action` ⇒ instance(0) | 6.0 s | 11 min 40 s |
| `local-devstral` (text-only) | **7/9** | 1/3 — m3n zufällig korrekt, x7q+p2k falsch | alle drei → `accessibilityId: Aktion` ⇒ instance(0) | 133.7 s | 37 min 4 s |

> **Hinweis zu OpenAI:** Das Profil wurde im Zuge dieser Messreihe von `gpt-4.1` (veraltet, Frühjahr 2025) auf `gpt-5.4-mini` aktualisiert für einen fairen Vergleich gegen Sonnet 4.6. Das Flagship `gpt-5.5` wurde ebenfalls verprobt: text-only erreichte 2/3 korrekte Toolbar-Heals (m3n→0, x7q→1) bevor das OpenAI-Quota erschöpft war — die ersten zwei Heals waren also tatsächlich korrekt, im Gegensatz zu sowohl `gpt-4.1` als auch `gpt-5.4-mini`. Damit liegt `gpt-5.5` qualitativ zwischen Sonnet (3/3) und den schwächeren Modellen (1/3), aber zu hohem Token-Aufwand (38 min Lauf, ~3× mehr Tokens pro Heal als gpt-4.1).

**Befund:**
- **Sonnet 4.6 ist auf dieser Strecke der Ausreißer** — 3/3 in jeder Iteration, auch nach Permutation der Page-Object-Deklarations-Reihenfolge. Source-Code-Order ist also **nicht** der Anker. Plausibel sind Reasoning aus dem broken-Locator-Suffix kombiniert mit dem XML-Page-Source oder ein Trainings-Prior für solche UI-Test-Patterns. **Keine der getesteten Härtungs-Hebel reicht aus, Sonnet auf dieser Strecke zu brechen.**
- **gpt-5.4-mini, Mistral Codestral, lokales Devstral** kollabieren alle: jeweils 1 von 3 Toolbar-Szenarien zufällig korrekt (immer m3n, weil instance(0) = Filter), der Rest scheitert auf der `toolbar_status`-Assertion. Das Failure-Muster ist nahezu identisch über alle drei: ein einziger statischer Locator (`By.id: toolbar_action` oder `accessibilityId: Aktion`) für alle drei Heals.
- **Vision macht für gpt-5.4-mini *keinen* Unterschied** — Screenshot wurde aktiv angehängt (3 × 140 KB), aber das Modell hat die visuelle Information für die Disambiguierung schlichtweg nicht genutzt. Vision-Mehrwert hängt also nicht nur an der Modellfähigkeit „kann Bilder interpretieren", sondern an „nutzt das Bild für die konkrete Locator-Entscheidung".
- Devstral verhält sich über alle vier Iterationen identisch (1/3, immer `accessibilityId: Aktion` → instance(0)) — die Suffix-Form und Quellcode-Reihenfolge sind für sein Verhalten irrelevant; die XML-Knoten-Kollision ist die ganze Geschichte.

### Hypothese-Status (nach Iteration 4 inkl. Cloud-Vergleich)

Die Vision-Hypothese hält für **Sonnet 4.6** auf dieser Strecke **nicht** — empirisch robust 3/3 über alle vier Härtungs-Iterationen, Mechanismus unklar (vermutlich Reasoning oder Training-Prior). Sie hält **klar** für **alle anderen getesteten Provider** (GPT-4.1, Mistral Codestral, Devstral lokal): jeder kollabiert auf 0–1 von 3 Toolbar-Heals, mit deterministischen Failure-Patterns auf der `toolbar_status`-Assertion. Damit ist der Track der erste **echte Diskriminator** im Benchmark-Setup — alle anderen Tracks waren über sämtliche Provider 6/6.

| Modell | Toolbar-Erfolg | Failure-Modus |
|---|---|---|
| Anthropic Sonnet 4.6 (text-only) | 3/3 ✓ | — |
| Anthropic Sonnet 4.6 (vision) | 3/3 ✓ | — |
| OpenAI gpt-5.4-mini (text-only) | 1/3 ✗ | alle drei → `By.id: toolbar_action` → instance(0) |
| OpenAI gpt-5.4-mini (vision) | 1/3 ✗ | alle drei → `accessibilityId: Aktion` → instance(0) (Bild ignoriert) |
| Mistral Codestral (text-only) | 1/3 ✗ | alle drei → `By.id: toolbar_action` → instance(0) |
| Devstral (local, text-only) | 1/3 ✗ | alle drei → `accessibilityId: Aktion` → instance(0) |

Damit ist der Track als **vision-affiner Lakmustest für 3 von 4 getesteten Modellen** dokumentiert. Eine Sonnet-brechende Härtung müsste vermutlich tiefer ansetzen — z. B. v2-Layout-Position randomisieren (Buttons in zufälliger Reihenfolge rendern), sodass selbst die XML-Knoten-Reihenfolge keine deterministische Position-Zuordnung mehr erlaubt. Das ist außerhalb des Scope des aktuellen Tracks.

```bash
# Strecke ausführen (Iterationen reproduzierbar über git checkout des jeweiligen Commits)
./scripts/run-tests-podman.sh v2 anthropic-vision   # Sonnet mit Screenshot (3/3)
./scripts/run-tests-podman.sh v2 anthropic          # Sonnet text-only (3/3)
./scripts/run-tests-podman.sh v2 openai-vision      # gpt-5.4-mini mit Screenshot (1/3 — Bild ignoriert)
./scripts/run-tests-podman.sh v2 openai             # gpt-5.4-mini text-only (1/3)
./scripts/run-tests-podman.sh v2 mistral            # Codestral (1/3)
./scripts/run-tests-podman.sh v2 local-devstral     # Devstral lokal (1/3)
```

---

## verify-fix.sh Validierung

```bash
./scripts/verify-fix.sh feature/my-fix              # Baseline (master) vs. Fix-Branch, v2 + Anthropic
./scripts/verify-fix.sh feature/my-fix openai       # mit anderem LLM-Provider
```

**Validierungs-Ergebnis:** Baseline 26/26 PASSED · Fix 26/26 PASSED · Szenario-Vergleich `[OK]` für alle 5 v2-Szenarien.

**Features:**
- Automatisches `git stash` / `git stash pop` für uncommitted Changes
- Infra (Emulator + Backend + Forwarder) startet nur **einmal** (`--no-deps`) — spart ~10 min gegenüber zweifachem Stack-Up
- Szenario-Labels: `[FIXED]`, `[REGRESSION]`, `[OK]`, `[STILL FAILING]`
- Reports unter `build/reports/verify/cucumber-{baseline,fix}.{html,json}`

