# Backlog

Done in Stage 5 (2026-10-21): object injection (WP-009), SSRF (WP-010), unsafe uploads (WP-011),
privilege escalation via role or capabilities (WP-012), and shortcode-attribute XSS (WP-013).

Still open:
- Inter-procedural taint: helpers in other functions or files, `$this->property` flows, values
  stored in the database (second-order SQLi, stored XSS through options or meta).
- realpath() + strpos() prefix guards on a derived variable (WP-007, CVE-2023-5355).
- Checks made in other functions for WP-011 (CF7-style validation filters).
- `header('Location: …')` as a WP-008 sink, and `$wp_filesystem` methods as WP-007 sinks.
- Output context awareness for WP-006 and WP-013 (attribute vs element vs URL vs JS).
- `upload_mimes` filter widening (part of the original unsafe-uploads item).
