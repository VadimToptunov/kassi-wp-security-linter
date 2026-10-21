# Kassi Security Inspections for WordPress

A PhpStorm plugin that adds static security inspections for WordPress plugin and theme code.
It is deterministic and offline: no network calls and no telemetry.

v0.1 inspections:

| ID | Inspection | CWE |
| --- | --- | --- |
| WP-001 | Missing capability check in `admin_post_*` / `wp_ajax_*` handlers | 862 |
| WP-002 | Missing nonce verification | 352 |
| WP-003 | Privileged action on a `wp_ajax_nopriv_*` hook | 862 |
| WP-004 | REST route without a real `permission_callback` | 862 |
| WP-005 | Unprepared SQL through `$wpdb` | 89 |
| WP-006 | Unescaped output | 79 |
| WP-007 | Unsafe file path from request input | 22 |
| WP-008 | Unsafe redirect | 601 |
| WP-009 | PHP object injection via `unserialize()` | 502 |
| WP-010 | Server-side request forgery via `wp_remote_*()` | 918 |
| WP-011 | Uploaded file stored without a type check | 434 |
| WP-012 | Role or capabilities from request data | 269 |
| WP-013 | Shortcode attributes output without escaping | 79 |

Every rule is backed by at least one real, fixed CVE in
[kassi-wp-corpus](https://github.com/VadimToptunov/kassi-wp-corpus): the vulnerable code must be
flagged and the fixed code must stay silent. The rules themselves are open (CC BY 4.0) in
[kassi-wp-rules](https://github.com/VadimToptunov/kassi-wp-rules).

**Report.** Tools | Kassi WP Security Report writes `kassi-wp-security-report.md` and `.json`
(deterministic, ready to use as a CI artifact).

**Settings.** Settings | Tools | Kassi WordPress Security has two options:
- excluded paths;
- an optional rule pack folder committed with the project. Its rules replace built-in rules by id.

Rule on/off and severity are set in the inspection profile.

The build commands are in `CLAUDE.md`. Licensed as proprietary; see `LICENSE`.
