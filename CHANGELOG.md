# Changelog

## 0.1.0 (unreleased)

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
- Intra-procedural taint engine. It understands WordPress sanitizers, allow-list guards and
  `sprintf` placeholders.
- Tools | Kassi WP Security Report: deterministic Markdown and JSON over the project.
- Settings | Tools | Kassi WordPress Security: excluded paths (default `vendor/`, `node_modules/`,
  `tests/`) and an optional project rule pack.
- Supports PhpStorm 2025.3 and later. No network calls, no telemetry.
