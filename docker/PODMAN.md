# Test-Stack mit Podman unter Windows/WSL2 ausführen

Dieses Dokument beschreibt, wie sich `docker-compose.yml` mit Podman unter Windows ausführen lässt — inklusive aller Setup-Schritte und typischer Stolperfallen aus dem ersten Setup.

## Voraussetzungen

- **Windows 11** mit aktiviertem WSL2
- **Podman Desktop** mit laufender WSL2-Maschine (`podman machine list` muss sie zeigen)
- Lokal installiertes **Android SDK** (zum Bauen der Test-APKs)
- **API-Keys** für Anthropic, OpenAI, Mistral (in `docker/.env` eintragen)

## Einmaliges Setup

### 1. KVM in der Podman-WSL2-Maschine aktivieren

Der `budtmo/docker-android`-Emulator benötigt `/dev/kvm` für Hardware-Beschleunigung — ohne KVM stürzt der Instrumentations-Prozess der App beim Start ab.

`C:\Users\<dein-user>\.wslconfig` anlegen oder ergänzen:

```ini
[wsl2]
nestedVirtualization=true
```

Anschließend in **PowerShell als Administrator**:

```powershell
wsl --shutdown
```

Ein paar Sekunden warten und dann prüfen, dass KVM in der Podman-Maschine verfügbar ist:

```bash
podman machine ssh ls /dev/kvm
# Ausgabe muss sein: /dev/kvm
```

Die Compose-Datei reicht `/dev/kvm` über folgenden Block in den Emulator-Container weiter:

```yaml
android-emulator:
  devices:
    - /dev/kvm
```

### 2. `.env` anlegen

```bash
cp docker/.env.example docker/.env
```

API-Keys für die zu benchmarkenden Provider eintragen.

### 3. Android-APKs bauen

Der Emulator-Service mountet `android-app/app/build/outputs/apk` read-only; die Tests greifen auf `v1/debug/app-v1-debug.apk` und `v2/debug/app-v2-debug.apk` zu. Diese müssen **vor** dem Stack-Start gebaut sein:

```bash
cd android-app
./gradlew assembleV1Debug assembleV2Debug
```

Unter Windows muss `ANDROID_HOME` auf das SDK zeigen:

```bash
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
```

Falls `gradle-wrapper.jar` fehlt, einmalig herunterladen:

```bash
curl -sL "https://raw.githubusercontent.com/gradle/gradle/v9.4.0/gradle/wrapper/gradle-wrapper.jar" \
     -o android-app/gradle/wrapper/gradle-wrapper.jar
```

## Stack ausführen

Standard-Testlauf (verwendet `LLM_PROVIDER` aus `.env`):

```bash
podman compose -f docker/docker-compose.yml up
```

Vollständiger Benchmark — führt alle drei Provider (Anthropic, OpenAI, Mistral) sequentiell aus:

```bash
podman compose -f docker/docker-compose.yml --profile benchmark up
```

Reports landen in `build/reports/` (auf den Host gemountet). Provider-spezifische Logs unter `build/reports/benchmark-<provider>.log`.

Stoppen und aufräumen:

```bash
podman compose -f docker/docker-compose.yml down
```

## Stolperfallen und Lösungen

### 1. `env file docker/.env not found`

Die Compose-Datei deklariert `env_file: .env` pro Service — der Pfad wird **relativ zum Verzeichnis der Compose-Datei** aufgelöst, nicht relativ zum `--env-file`-Flag. `docker/.env` muss tatsächlich existieren.

### 2. `./gradlew: not found` im Build

Ursache: `gradlew` hat **CRLF-Zeilenenden** (Windows-Default), und die Linux-Shell im Container findet den `#!/bin/sh`-Interpreter nicht.

Fix (bereits in den Dockerfiles aktiv):

```dockerfile
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
```

Langzeit-Fix: `.gitattributes` erzwingt LF für `gradlew` und `*.sh`.

### 3. `statfs .../android-app/app/build/outputs/apk: no such file or directory`

Das Volume-Mount setzt voraus, dass das Host-Verzeichnis **vor** dem Containerstart existiert. Anlegen (oder die APKs vorher bauen):

```bash
mkdir -p android-app/app/build/outputs/apk
mkdir -p build/reports
```

### 4. `The instrumentation process cannot be initialized` / App stürzt ab

Ursache: Der Android-Emulator läuft ohne KVM-Beschleunigung; die App stürzt direkt beim Start ab.

Fix: KVM wie in Schritt 1 aktivieren. In den Emulator-Logs muss `KVM activated` beim Start erscheinen.

### 5. Benchmark-Schleife: `sh: -Dcucumber.filter.tags=@self-healing: not found`

Ursache: YAML-`>`-Folded-Block-Scalar zerstört Shell-Line-Continuations (`\`). Der Backslash + Newline wird zu Backslash + Space — das ist keine Line-Continuation.

Fix (bereits aktiv):

```yaml
entrypoint:
  - sh
  - -c
  - |
    for provider in anthropic openai mistral; do
      SPRING_PROFILES_ACTIVE=$$provider LLM_PROVIDER=$$provider \
        ./gradlew :integration-tests:test --no-daemon --rerun \
        -Dcucumber.filter.tags='@self-healing' ...
    done
```

Hinweise:
- `$$provider` escaped das `$`, sodass Docker-Compose es als `$provider` an die Shell durchreicht (statt als Compose-Variable)
- `|` (Literal-Block-Scalar) erhält Newlines — keine Line-Continuation nötig
- `--rerun` zwingt Gradle dazu, den Test-Result-Cache zwischen Providern zu überspringen

### 6. Erster Provider scheitert: `Could not proxy command ... socket hang up`

Auch wenn der `android-emulator`-Healthcheck (prüft `boot_completed` und Appium `/status`) durchläuft, ist der UiAutomator2-Server im Emulator oft noch nicht bereit. Der erste `newSession`-Request scheitert dann mit *socket hang up*.

Fix (bereits im benchmark-runner-Entrypoint aktiv):
- `sleep 60` als Warm-up vor dem ersten Provider-Lauf
- 3-Versuche-Retry-Loop pro Provider mit 20 s Back-off

Kostet ~1 Minute, macht den Benchmark aber bei Cold-Starts deterministisch.

### 7. `RunRoot is pointing to a path (/run/user/1000/containers) which is not writable`

Diese Warnung erscheint, wenn `podman` direkt unter WSL2 ohne korrekt initialisiertes XDG-Runtime-Verzeichnis läuft. Stattdessen `podman.exe` aus Windows verwenden, oder WSL2 als ordentliche interaktive Session neu starten.

## Emulator-Verifikation

Der Emulator stellt eine noVNC-Ansicht bereit — sobald der Container healthy ist, ist sie unter http://localhost:6080 im Browser erreichbar. Appium läuft auf http://localhost:4723, ADB auf 5555.

```bash
# Emulator-Health prüfen:
podman inspect docker-android-emulator-1 --format '{{.State.Health.Status}}'
```
