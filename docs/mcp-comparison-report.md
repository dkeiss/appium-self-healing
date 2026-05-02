# MCP- vs. No-MCP Self-Healing Benchmark-Bericht

**Datum:** 2026-04-17 (Run 2 — mit Fixes)
**Track:** `@very-hard-navigation` (Fahrplan-Details hinter BottomSheet-Navigation)
**App-Version:** v2 (gegenüber v1 haben sich alle Locatoren geändert)
**Läufe pro LLM:** 1 × ohne MCP, 1 × mit MCP
**Cache:** deaktiviert (`SELF_HEALING_CACHE_ENABLED=false`)

### Fixes in diesem Run (gegenüber Run 1 vom 2026-04-17)

| Fix | Problem | Lösung |
|---|---|---|
| 1 | OpenAI: `NonTransientAiException` bei HTTP 429 bricht alle Tests ab | `spring.ai.retry.on-http-codes: [429]` → Spring AI behandelt 429 als transient und retried |
| 2 | Mistral: `No ToolCallback found for tool name: appium_find_elements` | `ENRICHMENT_SYSTEM_PROMPT` nennt nur noch exakte Tool-Namen (`appium_find_element` singular) |

---

## 1. Ergebnisübersicht

| Provider | MCP | Tests PASSED | Tests FAILED | Build | Gesamtdauer | Heals | ∅ Heal-Zeit |
|---|---|---|---|---|---|---|---|
| Anthropic (claude-sonnet-4-6) | ✗ | **6 / 6** | 0 | ✅ SUCCESSFUL | 14 min 48 s | 31 | 12 085 ms |
| Anthropic (claude-sonnet-4-6) | ✓ | **6 / 6** | 0 | ✅ SUCCESSFUL | 23 min 40 s | 31 | 15 656 ms |
| OpenAI (gpt-4.1) | ✗ | **6 / 6** | 0 | ✅ SUCCESSFUL |  9 min 54 s | 31 |  5 265 ms |
| OpenAI (gpt-4.1) | ✓ | **6 / 6** | 0 | ✅ SUCCESSFUL | 23 min 11 s | 31 | 20 400 ms |
| Mistral (codestral-latest) | ✗ | **5 / 6** | 1 | ❌ FAILED |  9 min 27 s | 36 |  3 775 ms |
| Mistral (codestral-latest) | ✓ | **6 / 6** | 0 | ✅ SUCCESSFUL |  9 min 37 s | 31 |  4 079 ms |

> **OpenAI+MCP:** Fix 1 erfolgreich — war im ersten Run 0/6, jetzt 6/6. ✅  
> **Mistral+MCP:** Fix 2 erfolgreich — kein Tool-Dispatch-Fehler mehr, 31/31 Enrichments abgeschlossen. ✅  
> **Mistral+noMCP:** weiterhin 5/6 (leg_platform-Locator schlägt fehl, selbe Ursache wie in Run 1).

---

## 2. Token-Verbrauch

| Provider | MCP | Triage-Tokens | Locator-Tokens | Gesamt-Tokens |
|---|---|---|---|---|
| Anthropic | ✗ | 98 093 | 215 040 | **313 133** |
| Anthropic | ✓ | 98 176 | 234 315 | **332 491** (+6,2 %) |
| OpenAI | ✗ | 79 536 | 175 029 | **254 565** |
| OpenAI | ✓ | 79 389 | 177 528 | **256 917** (+0,9 %) |
| Mistral | ✗ | 96 598 | 204 852 | **301 450** |
| Mistral | ✓ | 91 469 | 196 184 | **287 653** (−4,6 %) |

> Geschätzte Kosten (Preise Apr 2026):
> Anthropic Sonnet: $3/M input · $15/M output · ~85/15 % Split →
> nomcp ≈ **$1.50**, mcp ≈ **$1.60**
> OpenAI GPT-4.1: $2/M input · $8/M output · ~80/20 % Split →
> nomcp ≈ **$0.82**, mcp ≈ **$0.82**
> Mistral Codestral: $0.30/M input · $0.90/M output · ~85/15 % Split →
> nomcp ≈ **$0.12**, mcp ≈ **$0.11**

---

## 3. MCP-Enrichment-Diagnose

### Anthropic + MCP — technisch erfolgreich, inhaltlich leer

- **31 / 31** Enrichments abgeschlossen (`MCP enrichment complete`)
- Alle Enrichments meldeten: _"All three diagnostic tool calls failed … No Active Appium Session Found"_
- Ursache: `appium-mcp` läuft als eigener Prozess und benötigt eine **eigene** Appium-Session. Die vom Test gehaltene Session ist für externe Prozesse nicht direkt zugänglich.
- Overhead: +8 min 52 s Laufzeit, +19 275 Locator-Tokens (+9,0 %), +3 571 ms/Heal
- Qualitätsgewinn: keiner — Anthropic heilt erfolgreich ausschließlich via Page-Source-XML aus dem `FailureContext`.

### OpenAI + MCP — nach Fix stabil, aber teuer in Zeit

- **31 / 31** Enrichments abgeschlossen — kein `NonTransientAiException` mehr.
- Alle Enrichments meldeten: _"Unable to gather context … no active Appium session or driver found"_
- Token-Overhead minimal (+0,9 %), aber Laufzeit +13 min 17 s (+134 %).
- Ursache des Zeitoverheads: OpenAI braucht >13 s/Enrichment (MCP tool-call round trips + API latenz).
- Die 429-Retry-Konfiguration hat das Crash-Problem vollständig behoben.

### Mistral + MCP — Fix erfolgreich, leicht andere Heal-Qualität

- **31 / 31** Enrichments abgeschlossen — kein Tool-Dispatch-Fehler mehr.
- Mistral rief korrekt `appium_find_element` (singular) auf; alle Tool-Calls schlugen mit "no session" fehl.
- **Interessante Beobachtung:** Mistral+MCP hat `leg_platform` erfolgreich geheilt (`AppiumBy.accessibilityId: Gleis 9`), während Mistral+noMCP denselben Locator mit einem falschen ID-Vorschlag (`leg_item_0_platform`) scheitern ließ. Möglicherweise hat der Enrichment-Text (Tool-Fehler: "id-Strategie fehlgeschlagen") Mistral dazu veranlasst, statt ID eine AccessibilityId zu versuchen — oder es handelt sich um Zufall/Varianz.
- Laufzeit nahezu identisch (+10 s), Token-Verbrauch sogar leicht geringer (−4,6 %).

---

## 4. Bewertung, Bugs und Empfehlung

**Kernergebnis:** Technisch stabil, praktisch ohne Mehrwert. `appium-mcp` als externer Prozess kann die laufende `AppiumDriver`-Session nicht nutzen — er müsste eine neue Session am Gerät öffnen, was an der Session-Exklusivität scheitert. Das LLM erhält ohnehin bereits Page-Source-XML, Locator-Fehler und Page-Object-Code aus dem `FailureContext`; MCP-Tool-Calls liefern nur "No driver found"-Texte. Overhead: +9–13 min Laufzeit pro Run, kein messbarer Qualitätsvorteil.

| # | Problem | Status | Lösung |
|---|---|---|---|
| 1 | `NonTransientAiException` nach MCP-Enrichment | ✅ Behoben | `spring.ai.retry.on-http-codes: [429]` |
| 2 | `No ToolCallback found: appium_find_elements` | ✅ Behoben | Prompt auf exakte Tool-Namen eingeschränkt |
| 3 | MCP-Enrichment hängt am "No driver found"-Loop | ⚠️ Offen | Architekturproblem: Session-Sharing erforderlich |
| 4 | Mistral wiederholt halluzinierte ID bei Retries | ✅ Behoben | `rejectedLocators` in `FailureContext` |

**Empfehlung:** `self-healing.mcp.enabled` bleibt `false` (Standardwert). Falls MCP weiterentwickelt werden soll, müsste `appium-mcp` die Session-ID der laufenden Test-Session übergeben bekommen (`--session-id`), statt eine neue zu öffnen. Der MCP-Profil-Support im Stack bleibt für künftige Experimente erhalten.

---

## 5. Rejected-Locators Fix — Verification Run (17.04.2026)

Nach dem Commit `rejectedLocators` wurden alle 3 Provider erneut ohne MCP getestet:

| Provider | Tests | Build | Heals | ∅ Heal-Zeit | Tokens |
|---|---|---|---|---|---|
| Anthropic | **6/6** | ✅ | 31 | 12 219 ms | 313 479 |
| OpenAI | **6/6** | ✅ | 31 |  5 618 ms | 254 615 |
| Mistral | **6/6** | ✅ | 33 |  3 780 ms | 292 285 |

**Mistral `leg_platform` — Verlauf nach Fix:**
```
Attempt 1: By.id: leg_platform → By.id: leg_item_0_platform  ← nicht gefunden (halluziniert)
           [rejectedLocators: leg_item_0_platform] → Prompt für Attempt 2 enthält Verbot
Attempt 2: By.id: leg_platform → AppiumBy.accessibilityId: Gleis 9  ← SUCCESS ✅
```

Mistral ist jetzt **100 % (6/6)** — erstmals ohne MCP.

---

## 6. Devstral Small 2 lokal — @very-hard-navigation (17.04.2026)

Devstral Small 2 wurde auf demselben Track als lokales Referenz-Modell gegen die Cloud-Provider getestet:

| Metrik | Wert |
|---|---|
| Build | ✅ SUCCESSFUL · **6/6** |
| Heals | 31 · alle in Attempt 1 |
| ∅ Heal-Zeit | 100 344 ms (~100 s/Heal auf RTX 3090) |
| Tokens | 91 409 Triage + 194 104 Locator = **285 513** |
| Gesamtdauer | 70 min 9 s |
| Kosten | ~$0 (lokale GPU) |

Devstral heilt `leg_platform` direkt korrekt als `accessibilityId: Gleis 9` — Cloud-Mistral braucht dafür den `rejectedLocators`-Fix und zwei Attempts. Detaillierte Locator-Liste sowie Vergleich gegen Qwen3-Coder-30B und GLM-4.7-Flash siehe [TEST-RESULTS.md](../TEST-RESULTS.md#lokale-llms-im-detail).

| Provider | Tests | ∅ Heal-Zeit | Kosten/Run | Heals Attempt 1 |
|---|---|---|---|---|
| Anthropic claude-sonnet-4-6 | 6/6 ✅ | 12 219 ms | ~$1.50 | 31/31 |
| OpenAI gpt-4.1 | 6/6 ✅ | 5 618 ms | ~$0.82 | 31/31 |
| Mistral codestral-latest | 6/6 ✅ | 3 780 ms | ~$0.12 | 30/31 |
| **Devstral Small 2 (lokal)** | **6/6 ✅** | **100 344 ms** | **~$0** | **31/31** |
