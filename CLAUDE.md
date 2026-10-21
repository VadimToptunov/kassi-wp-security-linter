# Kassi Security Inspections for WordPress (kassi-wp-security-linter)

PhpStorm plugin: deterministic, offline static security inspections for WordPress plugin/theme
code. The full build spec is the "Kassi WP Security Linter — build prompt"; the decisions it left
open are recorded in `DECISIONS.md`.

## Hard rules
- **No runtime network calls, no telemetry, no runtime LLM.** Never "AI-powered" in any text.
- **Precision over coverage.** A false positive is a higher-priority bug than a miss. If a flow is
  not proven, the inspection stays silent.
- **Rules are data.** Sources, sanitizers and sinks live in YAML (the `kassi-wp-rules` repo); code
  reads them. Only YAML-listed functions count as sanitizers.
- `core/` imports nothing from IntelliJ, the PHP plugin or `plugin/` (`ModuleBoundaryTest`).
  `core` analyses run over `core.ast.AstNode`; `plugin/psi/PhpPsiAstNode` is the PSI adapter.
- New rules only when the user asks; each follows the definition of done (real-CVE corpus, FP run).
  v0.1 = WP-001..WP-013 (WP-009..WP-013 added in Stage 5 at the user's request, see DECISIONS.md).
- Never weaken a rule to pass the corpus; tighten it or log the case in `FALSE_POSITIVES.md`.
- No schema change without updating the validator and its tests.
- No `@Deprecated` platform API; check the SDK docs, not memory.
- Don't copy WPCS/PHPCS sniff code.
- Work in stages; after each stage, stop and report (done / failed / decisions made).
- No AI attribution in commits, PRs or files.

## Commands
```
./gradlew test                    # core unit tests + PHP PSI fixture tests
./gradlew verifyPluginStructure   # plugin.xml / descriptor structure
./gradlew verifyPlugin            # Plugin Verifier vs PhpStorm 2025.3 and newest (slow, downloads IDEs)
./gradlew buildPlugin             # plugin/build/distributions/*.zip
./gradlew runIde                  # sandbox PhpStorm with the plugin
git submodule update --init       # rules/ and corpus/cases/ (needed by the build and tests)
corpus/fetch-popular.sh && ./gradlew :plugin:test -Pkassi.fprun --tests '*FalsePositiveRun*'
                                  # false-positive run → plugin/build/reports/fp-run.md
```
Versions are pinned in `gradle.properties` with their source. Re-check them upstream before bumping.

## Layout
- `core/`: pure Kotlin. `ast/` (AstNode + traversal), `model/` (Rule), `kb/` (YAML loader +
  validator, `KnowledgeBase.loadDirectory`), `hooks/` (add_action discovery, same-file callable
  resolution, handler facts), `check/` (per-rule logic over AstNode; `Checks` is the
  rule id → `FileCheck` registry and the headless entry point), `flow/` (intra-procedural taint).
- `plugin/`: `psi/` (adapter), `rules/` (built-in rule base from resources), `inspections/` (thin PSI
  visitors + `Inspections.byRuleId`), `fixes/`, `report/` (Tools | Kassi WP Security Report),
  `settings/` (excluded paths, rule pack; per-project `ProjectRuleBase`).
- `rules/`: git submodule → `kassi-wp-rules` (public, CC BY 4.0). Copied into the plugin's resources at build time.
- `corpus/cases/`: git submodule → `kassi-wp-corpus` (public, GPL excerpts). `CorpusAcceptanceTest`
  enforces vulnerable → finding, fixed → silent for every rule reference.
- `corpus/fetch-popular.sh` + `popular-plugins.txt`: pinned top-20 plugins for the FP run (`corpus/plugins/`, git-ignored).

## Definition of done (per inspection)
1. `plugin/src/test/testData/<rule-id>/` fixtures: positive, negative, sanitizer, and the
   nopriv/GET exception where applicable (`<warning descr="...">` markup).
2. `inspectionDescriptions/<Name>.html` with vulnerable + fixed examples.
3. Registered in `plugin.xml` with `groupName="Kassi WordPress Security"`.
4. YAML rule with `cwe`, `references` (real CVE), `precision_notes`.
5. Corpus run: zero unexplained findings; disputed ones go in `FALSE_POSITIVES.md`.
6. At least one `kassi-wp-corpus` case: `vulnerable.php` flagged, `fixed.php` silent.
7. `./gradlew test verifyPlugin` green.
