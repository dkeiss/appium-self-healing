@toolbar
Feature: Werkzeugleisten-Aktionen auf der Ergebnisliste

  Die Ergebnis-Liste bietet drei Aktionen mit semantisch entkoppelten und
  unalphabetischen Locator-Namen (`btn_m3n` / `btn_x7q` / `btn_p2k`). Im v1-Layout
  sind sie als beschriftete TextButtons sichtbar (Filtern / Sortieren / Teilen). In
  v2 sind alle drei zu Icon-Buttons kollabiert, die sich denselben testTag
  (`toolbar_action`) und dieselbe content-description teilen — der Heal muss
  zwischen drei identischen XML-Knoten entscheiden, ohne dass der broken Locator
  Position oder Zweck verrät, und ohne dass der Suffix eine alphabetische
  Sortierung erlaubt. Verifikation läuft über den `toolbar_status`-Text, der nach
  erfolgreichem Klick die ausgelöste Aktion benennt — ein falscher Heal kippt damit
  auf der Assertion.

  Background:
    Given die App ist gestartet
    When ich "Berlin Hbf" als Startbahnhof eingebe
    And ich "München Hbf" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich mindestens 1 Verbindung

  Scenario: Button m3n löst Filter-Verhalten aus
    When ich auf den Button m3n klicke
    Then zeigt der Toolbar-Status "Verbindungen gefiltert"

  Scenario: Button x7q löst Sortier-Verhalten aus
    When ich auf den Button x7q klicke
    Then zeigt der Toolbar-Status "Verbindungen sortiert"

  Scenario: Button p2k löst Teilen-Verhalten aus
    When ich auf den Button p2k klicke
    Then zeigt der Toolbar-Status "Verbindungen geteilt"
