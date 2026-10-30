#!/usr/bin/env bash
# One command for the WP money loop: pick fresh unscanned targets, fetch their
# sources, run every inspection headless, and print the findings ranked by
# Patchstack payability. A human then reads the top candidates and files.
#
#   corpus/hunt.sh                      # 40 recently-updated, >=1000 installs
#   COUNT=60 MININSTALLS=5000 corpus/hunt.sh
#   BROWSE=popular corpus/hunt.sh       # top plugins instead of recently-updated
#   corpus/hunt.sh --skip-select        # reuse the existing corpus/hunt-batch.txt
#
# Downloaded plugin sources are only ever PARSED, never executed.
set -euo pipefail
cd "$(dirname "$0")/.."            # repo root
CORPUS=corpus
BATCH="$CORPUS/hunt-batch.txt"
HUNTDIR="$CORPUS/hunt"
REPORT="build/reports/fp-run.md"

BROWSE="${BROWSE:-updated}"
COUNT="${COUNT:-40}"
MININSTALLS="${MININSTALLS:-1000}"

if [[ "${1:-}" != "--skip-select" ]]; then
  echo "== 1/4 selecting fresh targets (browse=$BROWSE, count=$COUNT, >=$MININSTALLS installs) =="
  python3 "$CORPUS/hunt-select.py" --browse "$BROWSE" --count "$COUNT" \
    --min-installs "$MININSTALLS" --out "$BATCH"
else
  echo "== 1/4 reusing $BATCH =="
fi

echo
echo "== 2/4 fetching plugin sources into $HUNTDIR =="
# Re-fetch only new slugs; fetch-popular.sh skips ones already present.
LIST="hunt-batch.txt" OUT="hunt" "$CORPUS/fetch-popular.sh"

echo
echo "== 3/4 running inspections headless over $HUNTDIR =="
./gradlew --quiet :plugin:test -Pkassi.fprun="$PWD/$HUNTDIR" --tests '*FalsePositiveRun*' || {
  echo "gradle run failed — see output above" >&2; exit 1; }

echo
echo "== 4/4 triage (ranked by Patchstack payability) =="
python3 "$CORPUS/hunt-triage.py" "$REPORT" | tee "build/reports/hunt-triage.md"
echo
echo "candidates written to build/reports/hunt-triage.md"
echo "next: read the top (tier A/B, starred clusters), confirm by hand, file."
