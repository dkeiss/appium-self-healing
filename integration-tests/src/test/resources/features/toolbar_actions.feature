@toolbar
Feature: Werkzeugleisten-Aktionen auf der Ergebnisliste

  Die Ergebnis-Liste bietet drei Aktionen mit semantisch entkoppelten Locator-Namen
  (`btn_action_a/b/c`). Im v1-Layout sind sie als beschriftete TextButtons sichtbar
  (Filtern / Sortieren / Teilen). In v2 sind alle drei zu Icon-Buttons kollabiert,
  die sich denselben testTag (`toolbar_action`) und dieselbe content-description
  teilen — der Heal muss zwischen drei identischen XML-Knoten entscheiden, ohne dass
  der broken Locator (`btn_action_a` etc.) Position oder Zweck verrät. Verifikation
  läuft über den `toolbar_status`-Text, der nach erfolgreichem Klick die ausgelöste
  Aktion benennt — ein falscher Heal kippt damit auf der Assertion.

  Background:
    Given die App ist gestartet
    When ich "Berlin Hbf" als Startbahnhof eingebe
    And ich "München Hbf" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich mindestens 1 Verbindung

  Scenario: Aktion A löst Filter-Verhalten aus
    When ich auf Aktion A klicke
    Then zeigt der Toolbar-Status "Verbindungen gefiltert"

  Scenario: Aktion B löst Sortier-Verhalten aus
    When ich auf Aktion B klicke
    Then zeigt der Toolbar-Status "Verbindungen sortiert"

  Scenario: Aktion C löst Teilen-Verhalten aus
    When ich auf Aktion C klicke
    Then zeigt der Toolbar-Status "Verbindungen geteilt"
