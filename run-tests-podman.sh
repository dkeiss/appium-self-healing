#!/bin/bash
# ─── Appium Self-Healing Test Runner (Podman) ────────────────────────
# Identisch mit run-tests.sh, aber verwendet podman compose statt
# docker compose. Für Umgebungen ohne Docker-Desktop (z. B. Linux mit
# Podman oder Windows mit Podman Desktop).
#
# Usage:
#   ./run-tests-podman.sh                     # Run v1 tests with Claude (should pass)
#   ./run-tests-podman.sh v2                  # Run v1 tests against v2 app (triggers self-healing)
#   ./run-tests-podman.sh v2 openai           # Self-healing with GPT
#   ./run-tests-podman.sh benchmark           # Compare all LLMs
# ────────────────────────────────────────────────────────────────────

cd "$(dirname "$0")"

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
    cd android-app && ./gradlew assembleV1Debug assembleV2Debug --no-daemon && cd ..
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
