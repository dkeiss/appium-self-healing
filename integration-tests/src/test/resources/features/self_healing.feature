@self-healing
Feature: Self-Healing bei UI-Änderungen

  Demonstriert den Self-Healing-Mechanismus bei verschiedenen Arten von UI-Änderungen.
  Diese Tests sind mit v1-Locatoren geschrieben und werden gegen die v2-App ausgeführt.

  Background:
    Given die App ist gestartet
    And Self-Healing ist aktiviert

  Scenario: Einfache ID-Änderung wird geheilt
    When ich "Berlin Hbf" als Startbahnhof eingebe
    And ich "München Hbf" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich mindestens 1 Verbindung
    And der Self-Healing-Report zeigt alle geheilten Locatoren

  Scenario: Verbindungssuche mit Umstieg nach UI-Redesign
    When ich "Hamburg Hbf" als Startbahnhof eingebe
    And ich "Stuttgart Hbf" als Zielbahnhof eingebe
    And ich auf Suchen klicke
    Then sehe ich mindestens 1 Verbindung
    And die erste Verbindung hat mindestens 1 Umstieg
    And der Self-Healing-Report zeigt alle geheilten Locatoren
