#!/usr/bin/env python3
"""Rank the linter's raw findings by how likely they are to PAY on Patchstack.

Reads the markdown table written by FalsePositiveRun (build/reports/fp-run.md,
columns: Rule | Plugin | Location | Message | Line) and re-orders it so the
findings worth a human's triage time float to the top:

  payability tier of the rule  (Patchstack accepted-type mapping, 2026 rules)
    A  WP-005 SQLi · WP-009 object injection · WP-010 SSRF · WP-011 upload · WP-012 privesc
    B  WP-003 nopriv authz · WP-004 REST authz · WP-002 CSRF->write · WP-007 LFI/traversal
    C  WP-006 / WP-013 XSS (only if it executes immediately) · WP-001 missing cap
    D  WP-008 open redirect (Patchstack REJECTS this type)
  + cluster bonus: a plugin with several high-tier findings is systemically
    broken (pepro-class) and far more likely to yield a real, payable chain.

This does NOT decide truth — a human still reads the code. It decides ORDER,
so the pepro-class cases are looked at first instead of drowning under WP-001
notice-dismissal noise.

Usage:
  corpus/hunt-triage.py                       # reads build/reports/fp-run.md
  corpus/hunt-triage.py path/to/fp-run.md
"""
import collections, os, re, sys

TIER = {
    "WP-005": "A", "WP-009": "A", "WP-010": "A", "WP-011": "A", "WP-012": "A",
    "WP-003": "B", "WP-004": "B", "WP-002": "B", "WP-007": "B",
    "WP-006": "C", "WP-013": "C", "WP-001": "C",
    "WP-008": "D",
}
TIER_RANK = {"A": 0, "B": 1, "C": 2, "D": 3, "?": 4}
TIER_NOTE = {
    "A": "Patchstack top-tier (SQLi/object-inj/SSRF/upload/privesc)",
    "B": "Patchstack accepted (broken access control / CSRF->write / LFI)",
    "C": "conditional (XSS must auto-execute; cap depends on impact)",
    "D": "Patchstack REJECTS (open redirect) — skip unless it chains",
    "?": "unmapped rule",
}

ROW = re.compile(r"^\|\s*(WP-\d+)\s*\|\s*([^|]+?)\s*\|\s*`([^`]+)`\s*\|\s*(.*?)\s*\|\s*`?(.*?)`?\s*\|$")


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "build", "reports", "fp-run.md")
    if not os.path.isfile(path):
        print(f"no report at {path} — run the fp run first "
              f"(see corpus/hunt.sh)", file=sys.stderr)
        return 1

    # A finding the linter marked weak (". Weak: …" suffix) is reachable only by a privileged
    # victim, nonce-protected, or validated against an allow-list — not an exploit on its own. It is
    # kept but pushed below every real candidate and never counted toward a cluster, so a pile of
    # cap-gated/allow-listed handlers can't masquerade as a pepro-class find.
    WEAK = re.compile(r"\bWeak:", re.IGNORECASE)

    findings = []  # (rule, plugin, location, message, line, tier, weak)
    for ln in open(path, encoding="utf-8"):
        m = ROW.match(ln.rstrip("\n"))
        if not m:
            continue
        rule, plugin, loc, msg, line = m.groups()
        weak = bool(WEAK.search(msg))
        findings.append((rule, plugin.strip(), loc, msg, line, TIER.get(rule, "?"), weak))

    if not findings:
        print("no findings parsed (empty run, or format changed).")
        return 0

    strong = [f for f in findings if not f[6]]
    weak = [f for f in findings if f[6]]

    # Cluster bonus counts STRONG findings only, split by tier so an injection/privesc cluster
    # (real A-tier, pepro-class) is told apart from a pile of CSRF/authz handlers (B only).
    a_by_plugin = collections.Counter(f[1] for f in strong if f[5] == "A")
    hi_by_plugin = collections.Counter(f[1] for f in strong if f[5] in ("A", "B"))

    def sort_key(f):
        rule, plugin, loc, _msg, _line, tier, _weak = f
        return (TIER_RANK[tier], -hi_by_plugin[plugin], plugin, rule, loc)

    strong.sort(key=sort_key)
    weak.sort(key=sort_key)

    # Summary
    by_tier = collections.Counter(f[5] for f in strong)
    print("# Hunt triage — ranked by Patchstack payability\n")
    print(f"strong findings: {len(strong)}  "
          f"(A:{by_tier['A']} B:{by_tier['B']} C:{by_tier['C']} "
          f"D:{by_tier['D']} ?:{by_tier['?']})   weak/likely-FP: {len(weak)}\n")

    clusters = [(p, n) for p, n in hi_by_plugin.most_common() if n >= 2]
    if clusters:
        print("## Systemic clusters (>=2 strong high-tier findings — look here first)\n")
        for plugin, n in clusters:
            a = a_by_plugin[plugin]
            kind = f"{a} A-tier (injection/privesc — pepro-class)" if a else "B-tier only (access-control pile — usually low cash)"
            print(f"- **{plugin}** — {n} high-tier findings · {kind}")
        print()

    print("## Ranked candidates (strong)\n")
    print("| # | Tier | Rule | Plugin | Location | Message |")
    print("| --- | --- | --- | --- | --- | --- |")
    for i, (rule, plugin, loc, msg, line, tier, _) in enumerate(strong, 1):
        star = " ⭐" if hi_by_plugin[plugin] >= 2 and tier in ("A", "B") else ""
        print(f"| {i} | {tier}{star} | {rule} | {plugin} | `{loc}` | {msg[:80]} |")
        if i >= 120:
            print(f"| … | | | | | ({len(strong) - 120} more) |")
            break

    if weak:
        print("\n## Weak / likely-FP (capability-gated, nonce-protected, or allow-listed — skip unless chaining)\n")
        print("| Tier | Rule | Plugin | Location | Message |")
        print("| --- | --- | --- | --- | --- |")
        for rule, plugin, loc, msg, line, tier, _ in weak[:60]:
            print(f"| {tier} | {rule} | {plugin} | `{loc}` | {msg[:90]} |")
        if len(weak) > 60:
            print(f"| | | | | ({len(weak) - 60} more) |")

    print("\n## Tier legend")
    for t in ("A", "B", "C", "D", "?"):
        print(f"- **{t}**: {TIER_NOTE[t]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
