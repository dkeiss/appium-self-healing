Feature: Zugverbindung suchen

  Die App ermöglicht es, Zugverbindungen zwischen zwei Bahnhöfen zu suchen.
  Bei UI-Änderungen (v2) soll der Self-Healing-Mechanismus die Tests reparieren.

  Background:
    Given die App ist gestartet

  Scenario: Direkte Verbindung finden
    When ich "Berlin Hbf" als Startbahnhof eingebe
    And ich "München Hbf" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich mindestens 1 Verbindung
    And die erste Verbindung zeigt "Berlin Hbf" als Start
    And die erste Verbindung zeigt "München Hbf" als Ziel

  Scenario: Verbindung mit Umstieg
    When ich "Hamburg Hbf" als Startbahnhof eingebe
    And ich "Stuttgart Hbf" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich mindestens 1 Verbindung
    And die erste Verbindung hat mindestens 1 Umstieg

  Scenario: Keine Verbindung gefunden
    When ich "Berlin Hbf" als Startbahnhof eingebe
    And ich "Atlantis" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich eine Meldung "Keine Verbindungen gefunden"
