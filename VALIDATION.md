# Validation on real plugins (2026-10-22)

Three independent checks. All tooling is in the repo, and plugin versions are pinned, so every run can
be reproduced.

## 1. Recall on full sources of real CVEs

`corpus/fetch-recall.sh`, then `./gradlew :plugin:test -Pkassi.recall --tests '*RecallRun*'`.

For each of the 31 kassi-wp-corpus cases, the complete vulnerable and fixed releases of the plugin
(56 wordpress.org zips) are downloaded and the rule is run on the full file, not the excerpt. A case
passes when two things hold:
- every line the rule flags in the excerpt is flagged in the full vulnerable file;
- no line of the fixed excerpt is flagged in the full fixed file.

**Result: 31 / 31** (13 rules).

The rules were also run over the whole fixed plugins. They reported 44 other findings in 5 of them,
and a sample of 25 was triaged by reading the code: 18 real weaknesses, 5 true but low impact, 2 false
positives. Two of the plugins have since been closed on wordpress.org for security reasons. One
finding matches an already-published unauthenticated SQL injection.

## 2. False positives on the 20 most popular plugins

`corpus/fetch-popular.sh`, then `./gradlew :plugin:test -Pkassi.fprun --tests '*FalsePositiveRun*'`.
The run covers 20,124 PHP files. 24 findings remain: 1 warning and 23 weak (after the follow-up
tightening in FALSE_POSITIVES.md). Each is triaged in
FALSE_POSITIVES.md.

## 3. Long tail: 138 plugins with 1k–50k installs

The plugins were picked deterministically from the wordpress.org popularity pages 10–67 and updated
within 2 years. The list is pinned in `corpus/longtail-plugins.txt`; 12 releases that are no longer
downloadable were dropped. The run covers 19,010 PHP files:
`LIST=longtail-plugins.txt OUT=longtail corpus/fetch-popular.sh`, then
`./gradlew :plugin:test -Pkassi.fprun=$PWD/corpus/longtail --tests '*FalsePositiveRun*'`.

The first run gave 179 findings, 122 of them warnings. A stratified sample of 102 findings (including
the residual ones from check 1) was triaged by reading the code:

| Verdict | Count |
| --- | --- |
| TP: real weakness | 25 |
| TP-LOW: true, negligible impact (notice dismissals, admin-only tools) | 36 |
| FP: claim false | 41 |

The rules were then **tightened, never weakened** (DECISIONS.md, "Validation"; FALSE_POSITIVES.md,
follow-up). After that, the run gives 122 findings, 52 of them warnings. On the triaged rows that can be matched (99 of 102):

| Verdict | Before | After |
| --- | --- | --- |
| TP (25) | 23 warnings + 2 weak | unchanged: **no real weakness was lost** |
| TP-LOW (33) | 25 warnings + 8 weak | 13 warnings + 20 weak |
| FP (41) | 36 warnings + 5 weak | 15 warnings + 5 weak + 21 no longer reported |

Precision of the warning tier on the triaged sample:
- real weakness: 27% before, **45%** after;
- claim literally true: 57% before, **71%** after.

### What the numbers mean
- The top-20 plugins are mature code. On them the rules are quiet: 1 warning across 20 plugins.
  The long tail is where the rules have to earn their place. There, roughly 4 in 10 warnings are a
  real weakness, about 3 in 10 are true but harmless, and about 3 in 10 are false positives.
- The false positives that remain have known mechanisms. Fixing them needs analysis the v0.1 engine
  does not do:
  - checks in other files: helper functions with arbitrary names, and classes that are never
    instantiated;
  - validators the rules do not recognise: `$ext != 'zip'`, `strstr($f, '/')` rejection,
    `!empty($map[$f])`;
  - dead branches and standalone legacy scripts.

  They are listed in BACKLOG.md.
- The sample found **one high-severity issue that is still unpatched** in a current long-tail plugin.
  Its details are withheld from this repository pending responsible disclosure.

## Limits
- The triage is a sample (102 of 223 findings), done by reading the code, not by running exploits.
- Recall was measured on the 31 corpus CVEs. Recall on vulnerabilities outside the corpus is not known.
