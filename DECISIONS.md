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

## Stage 3 decisions

- **Hook handler discovery (`core/hooks`)**: `add_action(<hook>, <callback>)` with the callback
  resolved **in the same file**: a named function, a `$this` / `self` / `static` / `__CLASS__` /
  `X::class` / `'Class'` method, `'Class::method'`, or an inline closure. The hook name's static
  prefix folds literal concatenations (`'wp_ajax_' . 'x'`). A handler is analysed together with one
  level of same-file helpers (`$this->x()`, `self::x()`, `x()`), for both sinks and checks.
- **Call patterns in YAML** (`CallTarget`): `fn`, `recv->method`, `->method`, with `*` wildcards.
  Wildcards exist because capability and nonce wrappers usually live in files the analysis does not
  see. This tightens precision; it is not an arbitrary-function sanitizer.
- **WP-001**: a nonce without a capability check is **weak**. A missing check on an `endpoint_prefixes`-only
  handler is a warning. The current user's own meta (`update_user_meta(get_current_user_id(), …)`)
  is not privileged. Handlers also on nopriv hooks are left to WP-003.
- **WP-002**: same discovery; handlers also on nopriv hooks are skipped (public endpoints).
- **WP-003**: the privileged-sink list deliberately excludes what public forms legitimately do
  (`wp_insert_post`, `$wpdb->insert`, `wp_create_user`, uploads, login cookies). A nonce is not
  authentication. `hash_equals` / `hash_hmac` (one-time-token flows) are.
- **WP-004 escalation**: an always-true callback on a write route becomes a warning when the handler
  (same file) itself calls a privileged sink with no capability check. Sinks reached only through
  helpers keep it weak.
- **`Checks` registry** in core (added during Stage 2 review) is the headless entry point. Tests pin
  rule base = engine checks = plugin inspections.
- **Corpus**: 6 new cases (CVE-2023-6966, CVE-2025-3063, CVE-2023-0554, CVE-2023-6968,
  CVE-2021-4362, CVE-2025-2266). Shopper Approved 2.1 has no SVN tag, so its vulnerable side comes
  from the release zip (noted in its meta.yml).

## Stage 4 decisions

- **WP-007** reuses the taint engine. `sink_args` maps each function to its path-argument indexes;
  `include`/`require` are sinks too. `$_FILES[...]['tmp_name' | 'size' | 'error']` are clean
  (`clean_keys`); `['name']` is input. `file_exists` is **not** a guard:
  `if (file_exists($f)) unlink($f)` is the classic vulnerable shape. A comparison on a guard call's
  result (`0 !== validate_file($f)`) now counts as a check, for all taint rules.
- **Settings** (`.idea/kassi-wp-security.xml`, shared with the team): excluded paths and an optional
  rule-pack folder. Rule on/off and severity are not duplicated here; they are the IDE inspection
  profile, which the report honours as well.
- **Rule pack**: same YAML format, loaded from a project folder (no network). A pack rule replaces the
  built-in rule with the same id. An invalid pack is reported once and ignored, and the built-in rules
  keep running. Pack rule ids that this engine does not implement are reported and skipped.
- **Report**: Tools | Kassi WP Security Report writes `kassi-wp-security-report.md` and `.json` to the
  project root. The output is deterministic (no timestamps, sorted, project-relative paths), so it
  diffs cleanly in CI and works as compliance evidence. The formatting logic lives in core (`Report`)
  and is tested, including JSON validity with a real parser (Jackson, test scope only).
- **WP-004** now runs on the same `RuleInspection` base and the project rule base, so packs and
  exclusions apply to it too.
- **Screenshots** are not produced by the build: see MARKETPLACE.md for the manual checklist using
  `docs/demo/demo-plugin.php`.
- **Scope change (user decision, 2026-10-21):** "add more rules". The v0.1-only restriction in the
  spec is lifted for the BACKLOG items, which are planned as Stage 5: WP-009 object injection,
  WP-010 SSRF, WP-011 unsafe upload, WP-012 privilege escalation via role/capabilities, WP-013
  shortcode-attribute XSS. Each follows the same definition of done (real-CVE corpus, FP run).

## Stage 5 decisions (WP-009 … WP-013)

- **Path-sensitive if/else in the taint engine.** Each branch starts from the pre-`if` state; the
  then-branch gets the condition's positive guards; the implicit fall-through gets its negative
  guards; paths that terminate are dropped; the join is may-taint (tainted on any reaching path).
  The Wholesale case (CVE-2026-12144: assigned from `$_POST` in `if`, constant in `else`) needed it.
  The earlier "early exit after `!guard`" rule is now a special case. This change replaces the Stage 2
  "no branch joins" design; all earlier fixtures and corpus cases still pass.
- **Element-level taint**: `$a['k'] = v` strongly updates that element (and weakly the array).
- **WP-009**: `unserialize($x, ['allowed_classes' => false])` is not a sink.
- **WP-010**: `wp_safe_remote_*` is the fix and not a sink. A fixed `scheme://host/` prefix, a constant
  base or a clean `%s/` base cleans the URL (`host_prefix_cleans`). A constant base is assumed to end at
  the host.
- **WP-011** is a presence check, not taint: `move_uploaded_file` in a function with no type-check
  call anywhere in it. A literal destination extension is safe. Checks in other functions are not
  seen (the CF7 false positive, accepted and documented).
- **WP-012**: sinks are role methods, the `'role'` of `wp_insert_user`/`wp_update_user` (or non-literal
  args), and `*capabilities` user meta. `get_role()` is deliberately not a guard.
- **WP-013**: the source is the shortcode callback's first parameter. `sanitize_text_field` is not an
  escaper here (CVE-2024-11199: it keeps quotes). Temporary shortcodes (registered and removed in one
  function) are skipped.
- **Levels**: ERROR for WP-009, WP-011, WP-012 (critical); WARNING for WP-010, WP-013.
