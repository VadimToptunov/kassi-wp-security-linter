#!/usr/bin/env bash
# One command for the WP money loop: pick fresh unscanned targets, fetch their
# sources, run every inspection headless, and print the findings ranked by
# Patchstack payability. A human then reads the top candidates and files.
#
#   corpus/hunt.sh                      # 40 recently-updated, 1000-20000 installs, <=18mo old
#   SURFACE=1 corpus/hunt.sh            # rotate high-risk feature keywords (best EV — less-audited surface)
#   COUNT=60 MININSTALLS=2000 MAXINSTALLS=40000 corpus/hunt.sh
#   MAXAGE=365 corpus/hunt.sh           # tighter responsive-vendor gate (updated within a year)
#   BROWSE=top-rated corpus/hunt.sh     # browse mode instead of recently-updated
#   corpus/hunt.sh --skip-select        # reuse the existing corpus/hunt-batch.txt
#
# Downloaded plugin sources are only ever PARSED, never executed.
set -euo pipefail
cd "$(dirname "$0")/.."            # repo root
CORPUS=corpus
BATCH="$CORPUS/hunt-batch.txt"
HUNTDIR="$CORPUS/hunt"
REPORT="plugin/build/reports/fp-run.md"

BROWSE="${BROWSE:-updated}"
COUNT="${COUNT:-40}"
MININSTALLS="${MININSTALLS:-1000}"
MAXINSTALLS="${MAXINSTALLS:-20000}"   # band ceiling: skip mega-popular, picked-clean plugins
MAXAGE="${MAXAGE:-540}"               # responsive-vendor gate (days); 0 disables
SURFACE="${SURFACE:-}"                # SURFACE=1 → rotate high-risk feature keywords (best EV)

if [[ "${1:-}" != "--skip-select" ]]; then
  SEL=(--count "$COUNT" --min-installs "$MININSTALLS" --max-installs "$MAXINSTALLS" \
       --max-age-days "$MAXAGE" --out "$BATCH")
  if [[ -n "$SURFACE" ]]; then
    echo "== 1/4 selecting fresh targets (surface keywords, count=$COUNT, ${MININSTALLS}-${MAXINSTALLS} installs, <=${MAXAGE}d) =="
    SEL=(--surface "${SEL[@]}")
  else
    echo "== 1/4 selecting fresh targets (browse=$BROWSE, count=$COUNT, ${MININSTALLS}-${MAXINSTALLS} installs, <=${MAXAGE}d) =="
    SEL=(--browse "$BROWSE" "${SEL[@]}")
  fi
  python3 "$CORPUS/hunt-select.py" "${SEL[@]}"
else
  echo "== 1/4 reusing $BATCH =="
fi

echo
echo "== 2/4 fetching plugin sources into $HUNTDIR =="
# Re-fetch only new slugs; fetch-popular.sh skips ones already present.
LIST="hunt-batch.txt" OUT="hunt" "$CORPUS/fetch-popular.sh"

echo
echo "== 3/4 running inspections headless over $HUNTDIR =="
./gradlew --quiet --no-watch-fs :plugin:test -Pkassi.fprun="$PWD/$HUNTDIR" --tests '*FalsePositiveRun*' || {
  echo "gradle run failed — see output above" >&2; exit 1; }

echo
echo "== 4/4 triage (ranked by Patchstack payability) =="
python3 "$CORPUS/hunt-triage.py" "$REPORT" | tee "plugin/build/reports/hunt-triage.md"

# Record the scanned slugs so hunt-select.py excludes them next time (it reads
# every *-plugins.txt). Accumulates across runs.
LEDGER="$CORPUS/scanned-plugins.txt"
[[ -f "$LEDGER" ]] || echo "# slugs already scanned by hunt.sh — excluded from future selections" > "$LEDGER"
grep -v '^#' "$BATCH" | awk '{print $1}' >> "$LEDGER"
# de-dup, keep header
{ head -1 "$LEDGER"; grep -v '^#' "$LEDGER" | sort -u; } > "$LEDGER.tmp" && mv "$LEDGER.tmp" "$LEDGER"

echo
echo "candidates written to plugin/build/reports/hunt-triage.md"
echo "$(grep -vc '^#' "$LEDGER") slugs now in $LEDGER (excluded next run)"
echo "next: read the top (tier A/B, starred clusters), confirm by hand, file."
