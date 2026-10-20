# NFL Super Bowl & Playoff Survival Odds

default:
    @just --list

# Fetch live ESPN data, simulate, refresh charts/datasets/README, and sync PocketHost (required)
refresh iterations="20000":
    ./gradlew run -q -PsimArgs="--iterations {{iterations}}"

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

# Refresh, verify, and commit the new snapshot
publish: refresh test
    git add README.md docs output
    git commit -m "chore: data refresh $(date +%F)" || echo "No changes to commit"
