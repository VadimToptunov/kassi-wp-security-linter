# Kassi Security Inspections for WordPress

Static security inspections for WordPress plugin and theme code in PhpStorm. Deterministic and
offline: no network calls, no telemetry.

| ID | Inspection | CWE |
| --- | --- | --- |
| WP-001 | AJAX / admin-post handler changes state without a capability check | 862 |
| WP-002 | AJAX / admin-post handler changes state without nonce verification | 352 |
| WP-003 | Privileged action on an unauthenticated (`nopriv`) hook | 862 |
| WP-004 | REST route without a real `permission_callback` | 862 |
| WP-005 | SQL built from request data without `$wpdb->prepare()` | 89 |
| WP-006 | Request data output without escaping | 79 |
| WP-007 | File path built from request data | 22 |
| WP-008 | `wp_redirect()` to a URL from request data | 601 |
| WP-009 | PHP object injection via `unserialize()` | 502 |
| WP-010 | Server-side request forgery via `wp_remote_*()` | 918 |
| WP-011 | Uploaded file stored without a type check | 434 |
| WP-012 | Role or capabilities taken from request data | 269 |
| WP-013 | Shortcode attributes output without escaping | 79 |

Every rule is backed by real, fixed CVEs in
[kassi-wp-corpus](https://github.com/VadimToptunov/kassi-wp-corpus): the vulnerable code must be
flagged and the fixed code must stay silent. The rules are open (CC BY 4.0) in
[kassi-wp-rules](https://github.com/VadimToptunov/kassi-wp-rules).

**Report:** Tools | Kassi WP Security Report writes `kassi-wp-security-report.md` and `.json`.
**Settings:** Settings | Tools | Kassi WordPress Security (excluded paths, optional rule pack).

## Build

```
git submodule update --init
./gradlew test verifyPlugin buildPlugin
```

Requires JDK 21. Licensed as proprietary; see `LICENSE`.
