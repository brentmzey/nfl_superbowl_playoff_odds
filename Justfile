# NFL Super Bowl & Playoff Survival Odds

default:
    @just --list

# Fetch live ESPN data, simulate, refresh charts/datasets/README, and sync PocketHost (required)
refresh iterations="20000":
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -z "${POCKETHOST_PASSWORD:-}" ] && ! grep -qE '^POCKETHOST_PASSWORD=.+' .env 2>/dev/null; then
        echo "POCKETHOST_PASSWORD is not set. Run: just set-password   (or: just refresh-local)" >&2
        exit 1
    fi
    ./gradlew run -q -PsimArgs="--iterations {{iterations}}"

# Prompt (hidden input) for the PocketHost superuser password and save it to the git-ignored .env
set-password:
    #!/usr/bin/env bash
    set -euo pipefail
    touch .env && chmod 600 .env
    user=$(grep -E '^POCKETHOST_USER=' .env | cut -d= -f2- || true)
    read -rsp "PocketHost superuser password${user:+ for $user}: " pw; echo
    [ -n "$pw" ] || { echo "Nothing entered; .env unchanged." >&2; exit 1; }
    tmp=$(mktemp)
    grep -v '^POCKETHOST_PASSWORD=' .env > "$tmp" || true
    printf 'POCKETHOST_PASSWORD=%s\n' "$pw" >> "$tmp"
    mv "$tmp" .env && chmod 600 .env
    unset pw
    echo "Saved to .env (git-ignored). Next: just refresh"

# Same refresh without the PocketHost database
refresh-local iterations="20000":
    ./gradlew run -q -PsimArgs="--iterations {{iterations}} --db off"

# Start local PostgreSQL from ~/personal/local-db-stack (localhost:15432) for the replica and its tests
db-up:
    docker compose -f ~/.local-db-stack/docker-compose.yml up -d postgres

# Check that the configured PocketHost instance is reachable
db-health:
    @set -a; [ -f .env ] && . ./.env; set +a; curl -fsS "$POCKETHOST_URL/api/health" && echo

# Tests + coverage report + >=90% line/branch gate
test:
    ./gradlew check jacocoTestReport

# Regenerate the TypeScript typings from PocketBaseSchema.kt (no network, no DB)
schema-ts:
    ./gradlew run -q -PsimArgs="--db off --iterations 200"

# TypeScript client: tests + coverage, strict typecheck, build
ts-check:
    cd clients/ts && bun install --frozen-lockfile && bun run check

# Kotlin + TypeScript gates
check-all: test ts-check

# Refresh, verify, and commit the new snapshot
publish: refresh test
    git add README.md docs output
    git commit -m "chore: data refresh $(date +%F)" || echo "No changes to commit"
