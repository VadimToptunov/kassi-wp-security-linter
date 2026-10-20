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
