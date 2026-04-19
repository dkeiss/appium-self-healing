#!/bin/bash
# ─── Appium Self-Healing Test Runner ────────────────────────────────
# Usage:
#   ./run-tests.sh                     # Run v1 tests with Claude (should pass)
#   ./run-tests.sh v2                  # Run v1 tests against v2 app (triggers self-healing)
#   ./run-tests.sh v2 openai           # Self-healing with GPT
#   ./run-tests.sh benchmark           # Compare all LLMs
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
echo "║  Appium Self-Healing Test Runner                 ║"
echo "║  App Version: $APP_VERSION                       ║"
echo "║  LLM Provider: $LLM_PROVIDER                     ║"
echo "╚══════════════════════════════════════════════════╝"

cleanup() {
    echo "Cleaning up containers..."
    docker compose down 2>/dev/null
}

if [ "$APP_VERSION" = "benchmark" ]; then
    echo "Starting benchmark mode (comparing all LLMs)..."
    cd docker
    cleanup
    trap cleanup EXIT
    docker compose --profile benchmark up --build --force-recreate benchmark-runner
else
    export APP_VERSION
    export LLM_PROVIDER
    cd docker
    cleanup
    trap cleanup EXIT
    docker compose up --build --force-recreate test-runner
    # Preserve version-specific reports so subsequent runs don't overwrite them
    cp ../build/reports/cucumber.html "../build/reports/cucumber-${APP_VERSION}.html" 2>/dev/null
    cp ../build/reports/cucumber.json "../build/reports/cucumber-${APP_VERSION}.json" 2>/dev/null
fi

echo "Reports available at: build/reports/cucumber-${APP_VERSION:-v1}.html"
