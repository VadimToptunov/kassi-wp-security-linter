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

    findings = []  # (rule, plugin, location, message, line, tier)
    for ln in open(path, encoding="utf-8"):
        m = ROW.match(ln.rstrip("\n"))
        if not m:
            continue
        rule, plugin, loc, msg, line = m.groups()
        findings.append((rule, plugin.strip(), loc, msg, line, TIER.get(rule, "?")))

    if not findings:
        print("no findings parsed (empty run, or format changed).")
        return 0

    # Cluster bonus: how many A/B findings each plugin has.
    hi_by_plugin = collections.Counter(
        f[1] for f in findings if f[5] in ("A", "B"))

    def sort_key(f):
        rule, plugin, loc, msg, line, tier = f
        return (TIER_RANK[tier], -hi_by_plugin[plugin], plugin, rule, loc)

    findings.sort(key=sort_key)

    # Summary
    by_tier = collections.Counter(f[5] for f in findings)
    print("# Hunt triage — ranked by Patchstack payability\n")
    print(f"total findings: {len(findings)}  "
          f"(A:{by_tier['A']} B:{by_tier['B']} C:{by_tier['C']} "
          f"D:{by_tier['D']} ?:{by_tier['?']})\n")

    clusters = [(p, n) for p, n in hi_by_plugin.most_common() if n >= 2]
    if clusters:
        print("## Systemic clusters (>=2 high-tier findings — look here first, pepro-class)\n")
        for plugin, n in clusters:
            print(f"- **{plugin}** — {n} high-tier findings")
        print()

    print("## Ranked candidates\n")
    print("| # | Tier | Rule | Plugin | Location | Message |")
    print("| --- | --- | --- | --- | --- | --- |")
    for i, (rule, plugin, loc, msg, line, tier) in enumerate(findings, 1):
        star = " ⭐" if hi_by_plugin[plugin] >= 2 and tier in ("A", "B") else ""
        print(f"| {i} | {tier}{star} | {rule} | {plugin} | `{loc}` | {msg[:80]} |")
        if i >= 120:
            print(f"| … | | | | | ({len(findings) - 120} more) |")
            break

    print("\n## Tier legend")
    for t in ("A", "B", "C", "D", "?"):
        print(f"- **{t}**: {TIER_NOTE[t]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
