#!/bin/bash
# ─── Appium Self-Healing Test Runner (Podman) ────────────────────────
# Identisch mit run-tests.sh, aber verwendet podman compose statt
# docker compose. Für Umgebungen ohne Docker-Desktop (z. B. Linux mit
# Podman oder Windows mit Podman Desktop).
#
# Usage:
#   ./scripts/run-tests-podman.sh                # Run v1 tests with Claude (should pass)
#   ./scripts/run-tests-podman.sh v2             # Run v1 tests against v2 app (triggers self-healing)
#   ./scripts/run-tests-podman.sh v2 openai      # Self-healing with GPT
#   ./scripts/run-tests-podman.sh benchmark      # Compare all LLMs
# ────────────────────────────────────────────────────────────────────

cd "$(dirname "$0")/.."

APP_VERSION="${1:-v1}"
LLM_PROVIDER="${2:-anthropic}"

# Check for API keys
if [ ! -f docker/.env ]; then
    echo "ERROR: docker/.env not found. Copy docker/.env.example to docker/.env and set your API keys."
    exit 1
fi

# Build APKs if needed
if [ ! -f "android-app/app/build/outputs/apk/v1/debug/app-v1-debug.apk" ]; then
    echo "Building Android APKs..."
    (cd android-app && ./gradlew assembleV1Debug assembleV2Debug --no-daemon) || {
        echo "ERROR: APK build failed. See above."
        exit 1
    }
fi

# ─── Detect LM Studio host IP for local LLM providers ───────────────
# On Windows with Podman (WSL2), containers run inside the Podman
# machine and cannot resolve host.docker.internal automatically.
# We detect the vEthernet (WSL) adapter IP and export LM_STUDIO_URL
# so docker-compose passes the concrete IP into the container instead
# of the unresolvable hostname.
if [ -z "$LM_STUDIO_URL" ]; then
    # Try ipconfig (Windows/Git-Bash) first, fall back to hostname -I (WSL)
    WSL_HOST_IP=$(ipconfig 2>/dev/null \
        | awk '/WSL/{found=1} found && /IPv4/{match($0,/[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/); if(RLENGTH>0){print substr($0,RSTART,RLENGTH); exit}}')
    if [ -n "$WSL_HOST_IP" ]; then
        export LM_STUDIO_URL="http://${WSL_HOST_IP}:1234"
        echo "LM Studio URL (auto-detected WSL host): $LM_STUDIO_URL"
    else
        export LM_STUDIO_URL="http://host.docker.internal:1234"
        echo "LM Studio URL (fallback): $LM_STUDIO_URL"
    fi
fi

echo "╔══════════════════════════════════════════════════╗"
echo "║  Appium Self-Healing Test Runner (Podman)        ║"
echo "║  App Version: $APP_VERSION                       ║"
echo "║  LLM Provider: $LLM_PROVIDER                     ║"
echo "╚══════════════════════════════════════════════════╝"

cleanup() {
    echo "Cleaning up containers..."
    podman compose down 2>/dev/null
}

if [ "$APP_VERSION" = "benchmark" ]; then
    echo "Starting benchmark mode (comparing all LLMs)..."
    cd docker
    cleanup
    trap cleanup EXIT
    podman compose --profile benchmark up --build --force-recreate benchmark-runner
else
    export APP_VERSION
    export LLM_PROVIDER
    cd docker
    cleanup
    trap cleanup EXIT
    podman compose up --build --force-recreate test-runner
    # Preserve version-specific reports so subsequent runs don't overwrite them
    cp ../build/reports/cucumber.html "../build/reports/cucumber-${APP_VERSION}.html" 2>/dev/null
    cp ../build/reports/cucumber.json "../build/reports/cucumber-${APP_VERSION}.json" 2>/dev/null
fi

echo "Reports available at: build/reports/cucumber-${APP_VERSION:-v1}.html"
