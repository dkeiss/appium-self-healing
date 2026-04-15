# Running the Test Stack with Podman on Windows/WSL2

This document explains how to run `docker-compose.yml` with Podman on Windows, including all setup steps and common pitfalls encountered during first-time setup.

## Prerequisites

- **Windows 11** with WSL2 enabled
- **Podman Desktop** with a running WSL2 machine (`podman machine list` should show it)
- **Android SDK** installed locally (needed to build the test APKs)
- **API keys** for Anthropic, OpenAI, Mistral (put them in `docker/.env`)

## One-time setup

### 1. Enable KVM in the Podman WSL2 machine

The `budtmo/docker-android` emulator needs `/dev/kvm` for hardware acceleration,
otherwise the app's instrumentation process crashes.

Create or edit `C:\Users\<you>\.wslconfig`:

```ini
[wsl2]
nestedVirtualization=true
```

Then in **PowerShell as Administrator**:

```powershell
wsl --shutdown
```

Wait a few seconds, then verify KVM is available in the Podman machine:

```bash
podman machine ssh ls /dev/kvm
# should print: /dev/kvm
```

The compose file passes `/dev/kvm` into the emulator container via:

```yaml
android-emulator:
  devices:
    - /dev/kvm
```

### 2. Create `.env`

```bash
cp docker/.env.example docker/.env
```

Fill in the API keys for the providers you want to benchmark.

### 3. Build the Android APKs

The emulator service mounts `android-app/app/build/outputs/apk` read-only; the
tests refer to `v1/debug/app-v1-debug.apk` and `v2/debug/app-v2-debug.apk`.
These must be built **before** running the stack:

```bash
cd android-app
./gradlew assembleV1Debug assembleV2Debug
```

On Windows, make sure `ANDROID_HOME` points to your SDK:
```bash
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
```

If `gradle-wrapper.jar` is missing, download it once:
```bash
curl -sL "https://raw.githubusercontent.com/gradle/gradle/v9.4.0/gradle/wrapper/gradle-wrapper.jar" \
     -o android-app/gradle/wrapper/gradle-wrapper.jar
```

## Running the stack

Default test run (uses `LLM_PROVIDER` from `.env`):

```bash
podman compose -f docker/docker-compose.yml up
```

Full benchmark — runs all three providers (Anthropic, OpenAI, Mistral) sequentially:

```bash
podman compose -f docker/docker-compose.yml --profile benchmark up
```

Reports land in `build/reports/` (host-mounted). Per-provider logs:
`build/reports/benchmark-<provider>.log`.

To stop and clean up:

```bash
podman compose -f docker/docker-compose.yml down
```

## Pitfalls and fixes

### 1. `env file docker/.env not found`

The compose file declares `env_file: .env` per-service, which resolves
**relative to the compose file's directory**, not the `--env-file` flag.
Make sure `docker/.env` actually exists.

### 2. `./gradlew: not found` inside the build

Cause: `gradlew` has **CRLF line endings** (Windows default), and the Linux
shell inside the container can't find the `#!/bin/sh` interpreter.

Fix (already applied in the Dockerfiles):
```dockerfile
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
```

Long-term fix: `.gitattributes` enforces LF for `gradlew` and `*.sh`.

### 3. `statfs .../android-app/app/build/outputs/apk: no such file or directory`

The volume mount requires the host directory to exist **before** the container
starts. Create it (or build the APKs first):

```bash
mkdir -p android-app/app/build/outputs/apk
mkdir -p build/reports
```

### 4. `The instrumentation process cannot be initialized` / app crashes

Cause: The Android emulator runs without KVM acceleration; the app crashes
immediately on startup.

Fix: Enable KVM as described in step 1 above. Check that the emulator logs
`KVM activated` on startup.

### 5. Benchmark loop: `sh: -Dcucumber.filter.tags=@self-healing: not found`

Cause: YAML `>` folded block scalar breaks shell line continuations (`\`).
The backslash + newline becomes backslash + space, which is not a line continuation.

Fix (already applied):
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

Notes:
- `$$provider` escapes the `$` so Docker Compose passes it as `$provider` to the shell (not as a Compose variable)
- `|` literal block scalar preserves newlines — no line-continuation needed
- `--rerun` forces Gradle to skip its test-result cache between providers

### 7. First provider fails: `Could not proxy command ... socket hang up`

Even after the `android-emulator` healthcheck passes (checking
`boot_completed` and Appium `/status`), the UiAutomator2 server inside the
emulator often isn't fully ready yet. The first newSession request then fails
with a socket hang up.

Fix (already applied in benchmark-runner entrypoint):
- A `sleep 60` warm-up before the first provider run
- A 3-attempt retry loop per provider with 20s back-off

This sacrifices ~1 minute but makes the benchmark deterministic on cold starts.

### 6. `RunRoot is pointing to a path (/run/user/1000/containers) which is not writable`

That warning appears when running `podman` directly inside WSL2 without a
properly initialized XDG runtime directory. Use `podman.exe` from Windows
instead, or re-launch WSL2 as a proper interactive session.

## Verifying the emulator

The emulator exposes a noVNC view — open http://localhost:6080 in a browser
once the container is healthy. Appium is on http://localhost:4723, ADB on 5555.

```bash
# Check emulator health:
podman inspect docker-android-emulator-1 --format '{{.State.Health.Status}}'
```
