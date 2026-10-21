#!/usr/bin/env bash
# Recall check on full sources: downloads, for every kassi-wp-corpus case, the complete vulnerable
# and fixed releases of the plugin (wordpress.org zips) into corpus/recall/<slug>@<version>/.
# Archives are only parsed, never executed. Then:
#   ./gradlew :plugin:test -Pkassi.recall --tests '*RecallRun*'   → plugin/build/reports/recall-run.md
set -euo pipefail
cd "$(dirname "$0")"
OUT=recall
mkdir -p "$OUT"
python3 -I - cases <<'PY' | sort -u | while read -r slug version; do
import os, re, sys
root = sys.argv[1]
for dirpath, _, files in os.walk(root):
    if 'meta.yml' in files:
        meta = open(os.path.join(dirpath, 'meta.yml')).read()
        get = lambda k: re.search(rf'^{k}:\s*"?([^"\n]+)"?', meta, re.M).group(1).strip()
        print(get('plugin_slug'), get('version_before'))
        print(get('plugin_slug'), get('version_after'))
PY
  dest="$OUT/$slug@$version"
  [[ -d "$dest" ]] && continue
  tmp="$(mktemp -d)"
  if curl -sfL "https://downloads.wordpress.org/plugin/$slug.$version.zip" -o "$tmp/p.zip"; then
    unzip -q "$tmp/p.zip" -d "$tmp/x" && mv "$tmp/x/"*/ "$dest" && echo "fetched $slug $version"
  else
    echo "MISSING $slug $version (no release zip)"
  fi
  rm -rf "$tmp"
done
echo "done: $(ls "$OUT" | wc -l | tr -d ' ') releases in corpus/$OUT"
