#!/usr/bin/env bash
# False-positive corpus: the 20 most popular free plugins from wordpress.org, pinned by version.
#
#   corpus/fetch-popular.sh            download the pinned list (corpus/popular-plugins.txt)
#   corpus/fetch-popular.sh --refresh  re-pin: query the directory for the current top 20 first
#   LIST=longtail-plugins.txt OUT=longtail corpus/fetch-popular.sh
#                                      download another pinned list (the long-tail sample) into corpus/longtail/
#
# Archives are extracted under corpus/plugins/ (git-ignored) and only ever parsed, never executed.
# Run the inspections over them with:  ./gradlew :plugin:test -Pkassi.fprun --tests '*FalsePositiveRun*'
set -euo pipefail
cd "$(dirname "$0")"
LIST="${LIST:-popular-plugins.txt}"
OUT="${OUT:-plugins}"

if [[ "${1:-}" == "--refresh" ]]; then
  curl -sfg 'https://api.wordpress.org/plugins/info/1.2/?action=query_plugins&request[browse]=popular&request[per_page]=20&request[fields][versions]=0' \
    | python3 -c 'import json,sys; [print(p["slug"]+"@"+p["version"]+" "+p["download_link"]) for p in json.load(sys.stdin)["plugins"]]' > "$LIST.tmp"
  { echo "# slug@version download-url, pinned $(date -u +%Y-%m-%d) from the wordpress.org popular list"; cat "$LIST.tmp"; } > "$LIST"
  rm "$LIST.tmp"
fi

mkdir -p "$OUT"
grep -v '^#' "$LIST" | while read -r pinned url; do
  [[ -z "$pinned" ]] && continue
  slug="${pinned%@*}"; version="${pinned#*@}"
  dest="$OUT/$slug@$version"
  [[ -d "$dest" ]] && continue
  echo "fetching $slug $version"
  tmp="$(mktemp -d)"
  if curl -sfL "$url" -o "$tmp/p.zip" && unzip -q "$tmp/p.zip" -d "$tmp/x"; then
    # The archive holds one top-level folder (normally the slug).
    mv "$tmp/x/"*/ "$dest"
  else
    echo "  skipped: $url not available"
  fi
  rm -rf "$tmp"
done
echo "done: $(ls "$OUT" | wc -l | tr -d ' ') plugins in corpus/$OUT"
