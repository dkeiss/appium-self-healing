#!/bin/bash
# ─── PR Fix Verification ───────────────────────────────────────────
# Runs v2 tests on master (baseline) and on a fix branch,
# then compares the Cucumber results to verify the fix is effective.
#
# Optimization: The Android emulator + backend stay alive between runs.
# Only the test-runner container is rebuilt per branch, saving ~3-5 min.
#
# Usage:
#   ./verify-fix.sh <fix-branch>           # Compare master vs fix-branch
#   ./verify-fix.sh <fix-branch> openai    # Use a specific LLM provider
# ────────────────────────────────────────────────────────────────────

set -euo pipefail
cd "$(dirname "$0")"

FIX_BRANCH="${1:?Usage: $0 <fix-branch> [llm-provider]}"
LLM_PROVIDER="${2:-anthropic}"
BASE_BRANCH="master"
REPORT_DIR="build/reports/verify"
COMPOSE_FILE="docker/docker-compose.yml"

mkdir -p "$REPORT_DIR"

# ─── Prerequisites ──────────────────────────────────────────────────

if [ ! -f docker/.env ]; then
    echo "ERROR: docker/.env not found."
    exit 1
fi

if ! command -v jq &>/dev/null; then
    echo "ERROR: jq is required for report comparison. Install it with: choco install jq / brew install jq"
    exit 1
fi

# Verify fix branch exists
if ! git rev-parse --verify "$FIX_BRANCH" &>/dev/null; then
    echo "ERROR: Branch '$FIX_BRANCH' does not exist."
    exit 1
fi

# ─── Helpers ────────────────────────────────────────────────────────

run_test_runner() {
    local label="$1"
    echo ""
    echo "════════════════════════════════════════════════════"
    echo "  Running v2 tests: $label"
    echo "  LLM Provider: $LLM_PROVIDER"
    echo "════════════════════════════════════════════════════"
    echo ""

    # Build and run ONLY the test-runner (--no-deps skips recreating infra)
    APP_VERSION=v2 LLM_PROVIDER="$LLM_PROVIDER" \
        docker compose -f "$COMPOSE_FILE" up --build --force-recreate --no-deps test-runner

    # Copy reports with label
    cp build/reports/cucumber.json "$REPORT_DIR/cucumber-${label}.json" 2>/dev/null || true
    cp build/reports/cucumber.html "$REPORT_DIR/cucumber-${label}.html" 2>/dev/null || true

    # Remove test-runner container so next run gets a clean slate
    docker compose -f "$COMPOSE_FILE" rm -f test-runner 2>/dev/null || true
}

extract_results() {
    local json_file="$1"
    if [ ! -f "$json_file" ]; then
        echo "MISSING"
        return
    fi
    local total passed failed
    total=$(jq '[.[].elements[]? | select(.type == "scenario") | .steps[]? | select(.result)] | length' "$json_file")
    passed=$(jq '[.[].elements[]? | select(.type == "scenario") | .steps[]? | select(.result.status == "passed")] | length' "$json_file")
    failed=$(jq '[.[].elements[]? | select(.type == "scenario") | .steps[]? | select(.result.status == "failed")] | length' "$json_file")
    echo "${passed}/${total} passed, ${failed} failed"
}

wait_for_infra() {
    echo "Waiting for infrastructure to become healthy..."
    local retries=60
    while [ $retries -gt 0 ]; do
        local emulator_health backend_health
        emulator_health=$(docker compose -f "$COMPOSE_FILE" ps android-emulator --format '{{.Health}}' 2>/dev/null || echo "unknown")
        backend_health=$(docker compose -f "$COMPOSE_FILE" ps backend --format '{{.Health}}' 2>/dev/null || echo "unknown")
        if [[ "$emulator_health" == *"healthy"* ]] && [[ "$backend_health" == *"healthy"* ]]; then
            echo "Infrastructure is healthy."
            return 0
        fi
        echo "  Emulator: $emulator_health | Backend: $backend_health (retrying in 10s...)"
        sleep 10
        retries=$((retries - 1))
    done
    echo "ERROR: Infrastructure did not become healthy in time."
    return 1
}

# ─── Stash uncommitted changes ─────────────────────────────────────

STASHED=false
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Stashing uncommitted changes..."
    git stash push -m "verify-fix: auto-stash"
    STASHED=true
fi

ORIGINAL_BRANCH=$(git rev-parse --abbrev-ref HEAD)

cleanup() {
    echo ""
    echo "Cleaning up..."
    docker compose -f "$COMPOSE_FILE" down 2>/dev/null || true
    git checkout "$ORIGINAL_BRANCH" 2>/dev/null || true
    if [ "$STASHED" = true ]; then
        echo "Restoring stashed changes..."
        git stash pop 2>/dev/null || true
    fi
}
trap cleanup EXIT

# ─── Start infrastructure ONCE ─────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  Starting shared infrastructure...               ║"
echo "║  (Emulator + Backend + Forwarder)                ║"
echo "╚══════════════════════════════════════════════════╝"

docker compose -f "$COMPOSE_FILE" down 2>/dev/null || true
docker compose -f "$COMPOSE_FILE" up -d --build android-emulator backend backend-forwarder
wait_for_infra

# ─── Run 1: Baseline (master) ──────────────────────────────────────

git checkout "$BASE_BRANCH"
run_test_runner "baseline"

# ─── Run 2: Fix branch ─────────────────────────────────────────────

git checkout "$FIX_BRANCH"
run_test_runner "fix"

# ─── Compare Results ───────────────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  Verification Results                            ║"
echo "╠══════════════════════════════════════════════════╣"

BASELINE_RESULT=$(extract_results "$REPORT_DIR/cucumber-baseline.json")
FIX_RESULT=$(extract_results "$REPORT_DIR/cucumber-fix.json")

printf "║  %-10s %-37s ║\n" "Baseline:" "$BASELINE_RESULT"
printf "║  %-10s %-37s ║\n" "Fix:" "$FIX_RESULT"
echo "╠══════════════════════════════════════════════════╣"

# Detailed scenario comparison
if [ -f "$REPORT_DIR/cucumber-baseline.json" ] && [ -f "$REPORT_DIR/cucumber-fix.json" ]; then
    echo "║  Scenario Comparison:                            ║"

    # Extract scenario statuses (filter out background elements)
    jq -r '.[].elements[]? | select(.type == "scenario") | "\(.name): \(if [.steps[]?.result.status] | all(. == "passed") then "PASSED" else "FAILED" end)"' \
        "$REPORT_DIR/cucumber-baseline.json" > "$REPORT_DIR/scenarios-baseline.txt" 2>/dev/null || true
    jq -r '.[].elements[]? | select(.type == "scenario") | "\(.name): \(if [.steps[]?.result.status] | all(. == "passed") then "PASSED" else "FAILED" end)"' \
        "$REPORT_DIR/cucumber-fix.json" > "$REPORT_DIR/scenarios-fix.txt" 2>/dev/null || true

    # Show diff
    while IFS= read -r line; do
        scenario="${line%%:*}"
        baseline_status="${line##*: }"
        fix_status=$(grep -F "$scenario:" "$REPORT_DIR/scenarios-fix.txt" 2>/dev/null | head -1 | sed 's/.*: //')

        if [ "$baseline_status" = "FAILED" ] && [ "$fix_status" = "PASSED" ]; then
            marker="FIXED"
        elif [ "$baseline_status" = "PASSED" ] && [ "$fix_status" = "FAILED" ]; then
            marker="REGRESSION"
        elif [ "$baseline_status" = "PASSED" ] && [ "$fix_status" = "PASSED" ]; then
            marker="OK"
        else
            marker="STILL FAILING"
        fi
        printf "║    %-15s %s\n" "[$marker]" "$scenario"
    done < "$REPORT_DIR/scenarios-baseline.txt"
fi

echo "╚══════════════════════════════════════════════════╝"
echo ""
echo "Full reports: $REPORT_DIR/"
echo "  Baseline: $REPORT_DIR/cucumber-baseline.html"
echo "  Fix:      $REPORT_DIR/cucumber-fix.html"
