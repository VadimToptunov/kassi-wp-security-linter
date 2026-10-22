# Marketplace listing checklist

- **Name:** Kassi Security Inspections for WordPress (see DECISIONS.md, trademark).
- **Description and change notes:** in `plugin/src/main/resources/META-INF/plugin.xml`. They must
  never say "AI-powered".
- **Tags:** Security, PHP, WordPress, Inspections, Code Quality.
- **License:** proprietary; the rules are separate, under CC BY 4.0.
- **Screenshots (not yet taken):**
  1. Run `./gradlew runIde` and open a project containing `docs/demo/demo-plugin.php`.
  2. Take these four shots (every one of the 13 rules fires in the demo file; DemoFileTest guards it):
     the editor with WP-005 and WP-006 highlighted, the WP-004 popup and its
     quick-fix, the Inspections settings page with the "Kassi WordPress Security" group, and the
     generated `kassi-wp-security-report.md`.
  3. Use 1280×800 or larger, light theme, and no personal paths visible.

## First upload (manual, once)
1. Build: `./gradlew buildPlugin` → `plugin/build/distributions/kassi-wp-security-linter-0.1.0.zip`
   (or take the zip attached to the release PR).
2. plugins.jetbrains.com/plugin/add → upload the zip. Category: Code tools / Inspections; tags as
   above; license: proprietary (free to use); source code URL: the GitHub repo; add the screenshots.
3. After it is listed, add the repo secret `PUBLISH_PAT` (a Marketplace token, not a GitHub PAT).
   From then on, `git tag vX.Y.Z && git push origin vX.Y.Z` runs `.github/workflows/release.yml`
   (tests incl. corpus → verifier → publish), or queue the tag in the kassi-devtools rotation.
