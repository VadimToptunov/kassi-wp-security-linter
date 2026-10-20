# Backlog (not in v0.1; record only)

- Object injection via `unserialize` on input (CWE-502)
- Privilege escalation via `wp_capabilities` meta / `set_role` (CWE-269)
- Unsafe uploads: `upload_mimes` filter, `move_uploaded_file` without type checks (CWE-434)
- Unsafe shortcodes: unescaped attributes, shortcode output (CWE-79)
- SSRF via `wp_remote_get` / `wp_remote_post` with input URLs (CWE-918)
- Inter-procedural taint (v0.1 is intra-procedural only)
