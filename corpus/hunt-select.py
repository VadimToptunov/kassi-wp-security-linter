#!/usr/bin/env python3
"""Pick the next batch of WordPress plugins to hunt.

Lesson from 80 plugins across two batches with the improved pipeline: 0 new
payable. The popular + recently-updated top of wp.org is the WORST ground — those
plugins are the most-audited and most-actively-patched (form-maker/10Web harden
on every release; learnpress/masterstudy are big teams with their own fix history).
The one hit (pepro) came from the long-tail: older, smaller, less-audited, but not
abandoned. So this selector hunts that sweet spot instead:

  - an active_installs BAND [--min-installs, --max-installs], default 1000-20000:
    >=1000 is the Patchstack mVDP floor and keeps payout worth it; <=20000 skips
    the mega-popular plugins that have already been picked clean.
  - a RESPONSIVE-VENDOR gate: last_updated within --max-age-days (default 540 ~=
    18 months). Patchstack coordination needs a vendor who will still ship a fix;
    abandoned plugins are a reputation dead-end, not cash.
  - optional PATTERN targeting toward vulnerable surface (--surface, or --search
    KEYWORD): file upload / import / registration / REST / frontend-submit style
    features are where unauth + authz bugs actually live, and smaller plugins
    implement them without a security review.
  - slug NOT already in any *-plugins.txt / hunt-batch*.txt / scanned-plugins.txt
    (don't re-scan ground we've triaged).

Output: a pinned `slug@version download-url` list in the fetch-popular.sh format.

Usage:
  corpus/hunt-select.py                          # browse=updated, 1000-20000 installs, <=18mo old
  corpus/hunt-select.py --surface                # rotate high-risk feature keywords (best EV)
  corpus/hunt-select.py --search "file upload"   # one keyword
  corpus/hunt-select.py --browse top-rated --min-installs 2000 --max-installs 40000
  corpus/hunt-select.py --count 60 --max-age-days 365 --out corpus/hunt-batch.txt
"""
import argparse, datetime, json, os, re, sys, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
API = "https://api.wordpress.org/plugins/info/1.2/"

# Features that create unauth / authz / upload / injection surface and tend to be
# implemented without a security review in smaller plugins. Used by --surface.
SURFACE_KEYWORDS = [
    "file upload", "frontend file upload", "import export", "csv import",
    "user registration", "frontend registration", "membership", "frontend post",
    "frontend submit", "booking", "appointment", "job board", "directory listing",
    "download manager", "support ticket", "donation", "marketplace vendor",
    "quiz", "survey", "rest api", "ajax form", "image gallery upload", "document library",
]


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


def query(page: int, browse: str | None = None, search: str | None = None,
          per_page: int = 100) -> dict:
    params = {
        "action": "query_plugins",
        "request[page]": str(page),
        "request[per_page]": str(per_page),
        "request[fields][versions]": "0",
        "request[fields][description]": "0",
        "request[fields][sections]": "0",
        "request[fields][last_updated]": "1",
        "request[fields][active_installs]": "1",
    }
    if search:
        params["request[search]"] = search
    else:
        params["request[browse]"] = browse or "updated"
    url = API + "?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def last_updated_date(p: dict):
    """wp.org last_updated looks like '2024-01-15 3:45pm GMT' — take the date."""
    raw = (p.get("last_updated") or "").strip()
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})", raw)
    if not m:
        return None
    try:
        return datetime.date(int(m[1]), int(m[2]), int(m[3]))
    except ValueError:
        return None


def collect(sources, count, lo, hi, max_age_days, covered, per_source_cap=None):
    """sources: list of (label, kwargs-for-query). Returns picked rows + per-source counts.

    per_source_cap bounds how many picks one source contributes, so a batch samples diverse
    surface (upload, registration, booking, REST, ...) instead of 40 near-identical upload plugins
    that share one by-design pattern. A second pass with no cap backfills if the capped run is short.
    """
    today = datetime.date.today()
    picked: list[tuple[str, str, str, int]] = []
    seen: set[str] = set()
    per_source: dict[str, int] = {}
    for label, qkwargs in sources:
        if len(picked) >= count:
            break
        page = 1
        got_here = 0
        # Search results are relevance-ranked and shallow; browse is deep. Cap pages per source.
        max_pages = 6 if qkwargs.get("search") else 40
        while len(picked) < count and page <= max_pages:
            if per_source_cap is not None and got_here >= per_source_cap:
                break
            try:
                data = query(page, **qkwargs)
            except Exception as e:  # noqa: BLE001 — network hiccup on one source shouldn't abort the run
                print(f"  ! {label}: query failed ({e}); moving on", file=sys.stderr)
                break
            plugins = data.get("plugins", [])
            if not plugins:
                break
            for p in plugins:
                slug = p.get("slug", "")
                if not slug or slug in covered or slug in seen:
                    continue
                seen.add(slug)
                installs = int(p.get("active_installs", 0) or 0)
                if installs < lo or installs > hi:
                    continue
                if max_age_days is not None:
                    d = last_updated_date(p)
                    if d is None or (today - d).days > max_age_days:
                        continue
                version = p.get("version", "")
                url = p.get("download_link", "")
                if not version or not url:
                    continue
                picked.append((slug, version, url, installs))
                got_here += 1
                if len(picked) >= count or (per_source_cap is not None and got_here >= per_source_cap):
                    break
            page += 1
        per_source[label] = got_here
    return picked, per_source


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--browse", default="updated",
                    help="wp.org browse mode when not searching: updated (default), popular, new, top-rated")
    ap.add_argument("--search", default=None, help="one keyword/phrase to search instead of browse")
    ap.add_argument("--surface", action="store_true",
                    help="rotate the curated high-risk feature keywords (best EV); overrides --browse")
    ap.add_argument("--count", type=int, default=40)
    ap.add_argument("--min-installs", type=int, default=1000, help="band floor (Patchstack mVDP ~1000)")
    ap.add_argument("--max-installs", type=int, default=20000,
                    help="band ceiling — skip mega-popular, already-picked-clean plugins")
    ap.add_argument("--max-age-days", type=int, default=540,
                    help="skip plugins not updated within this many days (responsive-vendor gate); 0 disables")
    ap.add_argument("--out", default=os.path.join(HERE, "hunt-batch.txt"))
    args = ap.parse_args()

    if args.min_installs > args.max_installs:
        print("--min-installs must be <= --max-installs", file=sys.stderr)
        return 2
    max_age = None if args.max_age_days <= 0 else args.max_age_days

    if args.search:
        sources = [(f"search:{args.search}", {"search": args.search})]
        mode = f"search={args.search!r}"
    elif args.surface:
        sources = [(f"surface:{kw}", {"search": kw}) for kw in SURFACE_KEYWORDS]
        mode = f"surface ({len(SURFACE_KEYWORDS)} keywords)"
    else:
        sources = [(f"browse:{args.browse}", {"browse": args.browse})]
        mode = f"browse={args.browse}"

    covered = covered_slugs()
    if args.surface:
        # Spread across keywords: cap each so no single surface dominates the batch.
        cap = max(3, args.count // 8)
        picked, per_source = collect(sources, args.count, args.min_installs,
                                     args.max_installs, max_age, covered, per_source_cap=cap)
        if len(picked) < args.count:
            # Backfill uncapped from the same keywords if the capped pass came up short.
            already = covered | {p[0] for p in picked}
            more, more_src = collect(sources, args.count - len(picked), args.min_installs,
                                     args.max_installs, max_age, already)
            picked += more
            for lbl, n in more_src.items():
                per_source[lbl] = per_source.get(lbl, 0) + n
    else:
        picked, per_source = collect(sources, args.count, args.min_installs,
                                     args.max_installs, max_age, covered)

    stamp = datetime.date.today().isoformat()
    age_note = "any age" if max_age is None else f"updated <= {max_age}d"
    header = (f"# slug@version download-url — hunt batch pinned {stamp}: {mode}, "
              f"{args.min_installs}-{args.max_installs} installs, {age_note}, "
              f"excludes already-covered slugs ({len(covered)} known)")
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(header + "\n")
        for slug, version, url, installs in picked:
            f.write(f"{slug}@{version} {url}\n")

    print(f"selected {len(picked)} plugins ({mode}, "
          f"{args.min_installs}-{args.max_installs} installs, {age_note}) -> {args.out}")
    for slug, version, _, installs in picked[:15]:
        print(f"  {slug:40s} {version:14s} {installs:>10,} installs")
    if len(picked) > 15:
        print(f"  ... (+{len(picked) - 15} more)")
    if args.surface or args.search:
        hit_sources = [f"{lbl.split(':', 1)[1]}={n}" for lbl, n in per_source.items() if n]
        if hit_sources:
            print("  keywords that hit: " + ", ".join(hit_sources))
    if len(picked) < args.count:
        print(f"note: only {len(picked)} matched (wanted {args.count}); widen the band "
              f"(--max-installs), relax --max-age-days, or add --surface", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
