# Kassi Security Inspections for WordPress

A PhpStorm plugin that adds static security inspections for WordPress plugin and theme code.
It is deterministic and offline: no network calls and no telemetry.

v0.1 inspections (in progress):

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

The build commands are in `CLAUDE.md`. Licensed as proprietary; see `LICENSE`.
