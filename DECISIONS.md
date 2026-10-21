# Decisions

Closed before coding (2026-10-20).

| Question | Decision | Notes |
| --- | --- | --- |
| Minimum PhpStorm | **2025.3 (build 253)**, no until-build | Build/test target = 2025.3; the Plugin Verifier checks 2025.3 and the newest release (2026.2.3). Requires JDK 21. |
| Where `rules/` lives | **Separate public repo `kassi-wp-rules`** from day one | Consumed here as a git submodule at `rules/`. At build time it is copied into the plugin's resources under `kassi/wp-rules/`. |
| Marketplace name | **Kassi Security Inspections for WordPress** | "WordPress" is a third-party trademark. The "… for WordPress" form avoids implying an official product. |
| Plugin code license | **Proprietary, all rights reserved** | Source is public (source-available); distribution is via the JetBrains Marketplace. |
| Rules license | **CC BY 4.0** (in `kassi-wp-rules`) | Open reuse with attribution, so the rules get cited. |

## Build-tooling decisions made in Stage 0

- **IntelliJ Platform Gradle Plugin 2.x**, as the spec requires, not the 1.17.4 used in older Kassi plugins.
  1.x cannot target 2024.2+.
- **Kotlin compiler 2.4.20 with API/language level 2.2.** The IDE supplies kotlin-stdlib at runtime,
  so the plugin must not use a newer API than the floor IDE bundles. Stdlib is not bundled.
- **Gradle 9.8.0**: the newest release at least 2 weeks old.
- The **`AstNode` abstraction** uses a generic node with a kind, named roles (`AstRole`) and arguments,
  rather than a sealed class tree. That makes it cheap for any parser to adapt: unknown nodes map to
  `OTHER` and keep their children.

## Stage 1 decisions

- **WP-004 severity split.** A missing `permission_callback` on a write route is a warning. An
  always-true callback on a write route, or a missing callback on a read-only route, is a weak
  warning. Why: the top-20 plugin run produced 16 always-true write routes, all public on purpose
  (`FALSE_POSITIVES.md`). Plan: Stage 3 escalates them to a warning when the handler is in the same
  file and reaches a WP-003 privileged sink.
- **Rule logic lives in `core/check/`**, not in the inspection: the inspection is a PSI visitor that
  wraps the call and reports what `RestRouteCheck` returns, so any other engine reuses the same check.
- **`params:` added to the core rule schema** (optional mapping, rule-specific detection data such as
  HTTP method tables). The validator and its tests were updated, and the kassi-wp-rules README too.
- **Quick-fix for WP-004 only on read-only routes with no callback.** It adds `'__return_true'`,
  which keeps the current behaviour. Write routes get no auto-fix: the right capability is the
  plugin's call.
- **Unknown top-level YAML keys must be mappings** (another consumer's overlay). A scalar unknown
  key is rejected, so typos like `severty:` are caught.
- **Corpus cases:** CVE-2022-0218 (`'__return_true'`, POST), CVE-2022-0680 (CREATABLE, nested
  endpoint arrays), CVE-2026-9172 (no callback on a route declared GET that deletes data). Snippets
  were checked against plugins.svn.wordpress.org tags and the CVEs against NVD.

## Stage 2 decisions

- **Taint engine (`core/flow`)**: intra-procedural, statements in source order with strong updates,
  no branch joins. A conditional re-assignment can hide a flow but never invent one. Anything not
  proven tainted is clean: parameters, properties, unknown function results, closure `use` variables.
  That is how "a sanitizer counts only if it is in the YAML list" and "if the flow is not proven,
  stay silent" fit together: an unknown function neither cleans nor carries taint, it ends the
  proof. Only YAML `passthrough` functions carry taint.
- **Source notation** in `source:`: `$_GET`, `$_SERVER[REQUEST_URI]` / `$_SERVER[HTTP_*]`,
  `->get_param` (methods), `name` (functions). `$_SERVER` is a source only for client-controlled keys.
- **New rule params** (no schema change: `params` is free-form per rule, documented in each check):
  `passthrough`, `format_functions` (sprintf: taint only via `%s`), `guard_functions` + `terminators`
  (allow-list guards), `receivers` / `sink_methods` (WP-005), `sink_functions` /
  `query_arg_functions` (WP-008).
- **WP-005 includes `$wpdb->prepare()`'s first argument as a sink**: input concatenated into the
  format string is not escaped. `sanitize_text_field` passes taint through for SQL.
- **WP-006 is reflected-only** in v0.1 (no DB → output tracking). `sanitize_text_field` counts as
  safe for output: a known miss in attribute context, accepted for quiet. Output context is not checked.
- **WP-008**: `esc_url` / `esc_url_raw` pass taint through (they don't restrict the host);
  `add_query_arg` carries only its base-URL argument's taint. Quick-fix `wp_redirect` →
  `wp_safe_redirect` (same signature, the documented fix), offered only on proven flows.
- **Severity → level**: critical (WP-005) = ERROR; high/medium = WARNING.
- **Adapter**: expressions embedded in strings (`"{$a['k']}"`) are wrapped by PHP PSI in a nameless
  Variable. The wrapper is now transparent, which the Events Manager corpus case (CVE-2020-35012) needed.
- **Corpus**: 8 new cases from real CVEs (3 SQLi, 3 reflected XSS, 2 open redirect). Excerpts were
  generated verbatim by line range from SVN tags, and every file passes `php -l`. NVD's fixed version
  for CVE-2022-23911/23912 is off by one (1.4.8, not 1.4.7); recorded in meta.yml.
