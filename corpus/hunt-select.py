#!/usr/bin/env python3
"""Pick the next batch of WordPress plugins to hunt.

The long-tail corpus already covered a pinned sample (pages 10-67, every 10th).
This selects FRESH targets we have NOT scanned yet, biased toward where a
pepro-class finding is both likely and payable:

  - recently UPDATED plugins  -> new code = new attack surface, and a responsive
    maintainer (Patchstack needs a vendor who will ship a fix)
  - active_installs >= threshold (default 1000 = Patchstack mVDP floor; below
    100 is always out of scope)
  - slug NOT already in popular-plugins.txt / longtail-plugins.txt / any prior
    hunt-batch*.txt (don't re-scan ground we've triaged)

Output: a pinned `slug@version download-url` list in the fetch-popular.sh format.

Usage:
  corpus/hunt-select.py                      # 40 recently-updated, >=1000 installs
  corpus/hunt-select.py --browse popular     # or 'updated' (default) / 'new' / 'top-rated'
  corpus/hunt-select.py --count 60 --min-installs 5000 --out corpus/hunt-batch.txt
"""
import argparse, datetime, json, os, re, sys, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
API = "https://api.wordpress.org/plugins/info/1.2/"


def covered_slugs() -> set[str]:
    """Slugs already fetched/triaged — never re-scan them."""
    slugs: set[str] = set()
    for name in os.listdir(HERE):
        if not (name.endswith("-plugins.txt") or name.startswith("hunt-batch")):
            continue
        for line in open(os.path.join(HERE, name), encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            m = re.match(r"([a-z0-9\-]+)@", line)
            if m:
                slugs.add(m.group(1))
    return slugs


def query(browse: str, page: int, per_page: int = 100) -> dict:
    params = {
        "action": "query_plugins",
        "request[browse]": browse,
        "request[page]": str(page),
        "request[per_page]": str(per_page),
        "request[fields][versions]": "0",
        "request[fields][description]": "0",
        "request[fields][sections]": "0",
    }
    url = API + "?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--browse", default="updated",
                    help="wordpress.org browse mode: updated (default), popular, new, top-rated")
    ap.add_argument("--count", type=int, default=40)
    ap.add_argument("--min-installs", type=int, default=1000)
    ap.add_argument("--out", default=os.path.join(HERE, "hunt-batch.txt"))
    args = ap.parse_args()

    covered = covered_slugs()
    picked: list[tuple[str, str, str, int]] = []  # slug, version, url, installs
    seen: set[str] = set()
    page = 1
    while len(picked) < args.count and page <= 40:
        data = query(args.browse, page)
        plugins = data.get("plugins", [])
        if not plugins:
            break
        for p in plugins:
            slug = p.get("slug", "")
            if not slug or slug in covered or slug in seen:
                continue
            seen.add(slug)
            installs = int(p.get("active_installs", 0) or 0)
            if installs < args.min_installs:
                continue
            version = p.get("version", "")
            url = p.get("download_link", "")
            if not version or not url:
                continue
            picked.append((slug, version, url, installs))
            if len(picked) >= args.count:
                break
        page += 1

    stamp = datetime.date.today().isoformat()
    header = (f"# slug@version download-url — hunt batch pinned {stamp}: "
              f"browse={args.browse}, >={args.min_installs} installs, "
              f"excludes already-covered slugs ({len(covered)} known)")
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(header + "\n")
        for slug, version, url, installs in picked:
            f.write(f"{slug}@{version} {url}\n")

    print(f"selected {len(picked)} plugins -> {args.out}")
    for slug, version, _, installs in picked[:15]:
        print(f"  {slug:40s} {version:14s} {installs:>10,} installs")
    if len(picked) > 15:
        print(f"  ... (+{len(picked) - 15} more)")
    if len(picked) < args.count:
        print(f"note: only {len(picked)} matched (wanted {args.count}); "
              f"loosen --min-installs or try --browse popular", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
