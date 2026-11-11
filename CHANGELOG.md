# Changelog

## 1.3.0 (2026-11-11)

New inspections since 1.0.0, each backed by real CVE cases in kassi-wp-corpus:
- WP-014: Cross-plugin PHP object injection — a POP gadget chain reachable across plugins (CWE-502).
- WP-015: OS command injection — request data reaches `exec`/`shell_exec`/`system`/`proc_open`/backticks (CWE-78).
- WP-016: PHP code injection — request data reaches `eval()` or a dynamic include/callable (CWE-94).
- Joomla security pack: JL-005 (SQL injection), JL-006 (reflected XSS), JL-007 (local file inclusion),
  with an unauthenticated-reachability chain detector for file sinks.

Precision:
- Cross-file resolution: a handler whose capability or nonce gate lives in a separate helper file is
  no longer a false positive (WP-001 / WP-002 / WP-003).
- WP-009: a bounded `allowed_classes` list — `unserialize($x, ['allowed_classes' => ['A', 'B']])` —
  and the empty list are recognised as safe, not only `['allowed_classes' => false]`.
- WP-012: field-level taint of role/capability keys; a ternary allow-list or type guard cleans the
  protected branch.

Supports PhpStorm 2025.3 and later. No network calls, no telemetry.

## 1.0.0 (2026-10-22)

First release.

- Inspections, each backed by real CVE cases in kassi-wp-corpus:
  - WP-001: AJAX / admin-post handler changes state without a capability check (CWE-862)
  - WP-002: AJAX / admin-post handler changes state without nonce verification (CWE-352)
  - WP-003: Privileged action on an unauthenticated (nopriv) hook (CWE-862)
  - WP-004: REST route without a real `permission_callback` (CWE-862). Includes a quick-fix that
    declares `'__return_true'` explicitly on public read-only routes.
  - WP-005: SQL built from request data without `$wpdb->prepare()` (CWE-89)
  - WP-006: Request data output without escaping (CWE-79)
  - WP-007: File path built from request data (CWE-22)
  - WP-008: `wp_redirect()` to a URL from request data (CWE-601). Includes a quick-fix that
    switches to `wp_safe_redirect()`.
  - WP-009: PHP object injection via `unserialize()` / `maybe_unserialize()` of request data (CWE-502)
  - WP-010: Server-side request forgery via `wp_remote_*()` / `download_url()` (CWE-918)
  - WP-011: Uploaded file stored without a file type check (CWE-434)
  - WP-012: Request data decides a user role or capabilities (CWE-269)
  - WP-013: Shortcode attributes output without escaping, i.e. stored XSS (CWE-79)
- Intra-procedural taint engine. It joins if/else paths, tracks array elements, and understands
  WordPress sanitizers, allow-list guards and `sprintf` placeholders.
- Tools | Kassi WP Security Report: deterministic Markdown and JSON over the project.
- Settings | Tools | Kassi WordPress Security: excluded paths (default `vendor/`, `node_modules/`,
  `tests/`) and an optional project rule pack.
- Supports PhpStorm 2025.3 and later. No network calls, no telemetry.
