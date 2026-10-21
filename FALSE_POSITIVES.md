# False positives log

Each disputed finding from a corpus run: rule, plugin@version, file:line, why it fires, and the
decision (tighten the rule / accept and document / not a FP). Never weaken a rule just to make this
list shorter.

Run: `corpus/fetch-popular.sh`, then `./gradlew :plugin:test -Pkassi.fprun --tests '*FalsePositiveRun*'`
(report in `plugin/build/reports/fp-run.md`). Plugin versions are pinned in `corpus/popular-plugins.txt`.

## WP-004: run of 2026-10-20 (top-20 plugins, 410 files with a REST registration, 16 findings)

All 16 findings are `'permission_callback' => '__return_true'` on a write route. In every case the
route is public on purpose and the handler authenticates the request itself, or the action is
harmless when anonymous. The pattern is exactly the one behind CVE-2022-0218 and CVE-2022-0680; only
the handler body tells the two apart, and v0.1 does not follow it.

Triage method: route purpose, handler name and the route's declared args. The handlers were not
audited line by line, so the "why" column is an assessment, not a proof of safety.

**Decision:** report always-true callbacks on write routes as a **weak warning**, not a warning.
Stage 3 escalates them back to a warning when the handler is resolvable in the same file and
reaches a privileged sink (the WP-003 sink list). The corpus cases still fire, because a weak finding
counts. Missing callbacks on write routes stay a full warning (zero hits in this run).

| Plugin | Location | Route | Why it is public (assessed) |
| --- | --- | --- | --- |
| contact-form-7@6.2.2 | `includes/rest-api.php:115` | `POST /contact-forms/{id}/feedback` | Front-end form submission; the handler validates the form, spam checks |
| elementor@4.3.5 | `modules/feedback/module.php:31` | `POST elementor/v1/feedback/submit` | Feedback submission |
| jetpack@16.3.1 | `jetpack_vendor/automattic/woocommerce-analytics/src/API/class-wc-analytics-tracking-proxy.php:46` | `POST` tracking proxy | Anonymous analytics events |
| jetpack@16.3.1 | `jetpack_vendor/automattic/jetpack-connection/src/class-error-handler.php:1819` | `POST /verify_xmlrpc_error` | Connection handshake; the handler verifies a signed nonce |
| jetpack@16.3.1 | `jetpack_vendor/automattic/jetpack-connection/src/class-rest-connector.php:88` | `EDITABLE /verify_registration` | Connection handshake, signature-verified in the handler |
| jetpack@16.3.1 | `jetpack_vendor/automattic/jetpack-connection/src/class-rest-connector.php:100` | `EDITABLE /remote_authorize` | Connection handshake, signature-verified in the handler |
| jetpack@16.3.1 | `_inc/lib/core-api/wpcom-endpoints/class-wpcom-rest-api-v2-endpoint-ai.php:163` | `POST /jetpack-search/ai/rank` | Public search ranking for site visitors |
| litespeed-cache@7.9.2 | `src/rest.cls.php:87` | `POST litespeed/v3/wp_rest_echo` | REST availability self-test (echo) |
| woocommerce@11.2.1 | `vendor/automattic/jetpack-connection/src/class-error-handler.php:933` | `POST /verify_xmlrpc_error` | Same Jetpack connection code (vendored) |
| woocommerce@11.2.1 | `vendor/automattic/jetpack-connection/src/class-rest-connector.php:74` | `EDITABLE /verify_registration` | Same (vendored) |
| woocommerce@11.2.1 | `vendor/automattic/jetpack-connection/src/class-rest-connector.php:86` | `EDITABLE /remote_authorize` | Same (vendored) |
| woocommerce@11.2.1 | `src/Admin/API/MobileAppQRLogin.php:227` | `POST /qr-login-exchange` | Mobile QR login; the handler checks a one-time token |
| woocommerce@11.2.1 | `src/Admin/API/MobileAppQRLogin.php:305` | `POST /qr-login-scan` | Mobile QR login; the handler checks a one-time token |
| wordfence@9.0.2 | `lib/rest-api/wfRESTAuthenticationController.php:36` | `POST wordfence/v1/authenticate` | Central-service handshake; the handler verifies a signed nonce |
| wordpress-seo@28.7 | `src/ai/authorization/user-interface/callback-route.php:56` | `POST` AI OAuth callback | The handler checks the code verifier |
| wordpress-seo@28.7 | `src/ai/authorization/user-interface/refresh-callback-route.php:56` | `POST` AI OAuth refresh callback | The handler checks the code verifier |

## WP-005, WP-006, WP-008: run of 2026-10-21 (same top-20 plugins, 4,765 files scanned)

| Rule | Plugin | Location | Finding | Decision |
| --- | --- | --- | --- | --- |
| WP-005 | litespeed-cache@7.9.2 | `src/db-optm.cls.php:505` | `$_GET['litespeed_tb']` in `ALTER TABLE` | **Rule tightened.** The value is used only after `in_array($tb_param, $names, true)`, an allow-list of existing tables. The flow engine now understands allow-list guards (`params.guard_functions`): inside `if (guard($x))` and after `if (!guard($x)) { return/exit/wp_die; }`. Silent since then. |
| WP-008 | woocommerce@11.2.1 | `includes/admin/helper/class-wc-helper.php:1205` | `wp_redirect( wp_unslash( $_GET['wccom-install-url'] ) )` | **Kept (true pattern, hard to exploit).** The handler is gated by `wp_verify_nonce( $_GET['wc-helper-nonce'], 'connect' )` and an OAuth token exchange, so it cannot be used as a phishing link. The URL is still unvalidated; `wp_safe_redirect()` with an allowed-hosts filter would be correct. |

WP-006 produced no findings in this run.

## WP-001, WP-002, WP-003 and the WP-004 escalation: run of 2026-10-21 (11,986 files)

The first run gave 28 WP-001 findings, nearly all wrong. The rules were tightened (never weakened)
in these steps:

1. **Capability and nonce wrappers defined in other files.** Examples: `wpforms_current_user_can()`,
   `rsssl_user_can_manage()`, `UpdraftPlus_Options::user_can_manage()`. Rule lists now accept
   wildcard patterns (`*user_can*`, `->*user_can*`, `*verify_nonce*`). This removed 20 findings.
2. **Event reactors.** WooCommerce `EmailStyleSync` is hooked on `after_switch_theme` and similar
   hooks as well as on an AJAX hook. Handlers that are also registered on non-endpoint hooks are
   skipped (`endpoint_prefixes`).
3. **Nonce only, no capability check.** These became WP-001 **weak** warnings: they are exploitable
   only if the nonce reaches lower-privileged users.
4. **One-time token / HMAC flows** (the WPForms, WP Mail SMTP and WPCode connect/upgrade handlers):
   `hash_equals` / `hash_hmac` count as authentication for WP-003 and for the WP-004 escalation.
5. **WP-004 escalation counts only sinks in the handler's own body.** Jetpack's
   `verify_xml_rpc_error` and WooCommerce's QR login authenticate with a request secret and reach
   their writes through helpers.

What remains:

| Rule | Plugin | Location | Decision |
| --- | --- | --- | --- |
| WP-001 (weak) | duplicate-page@4.5.9 | `duplicatepage.php:408` | Kept. A nonce-checked "close help" option write with no capability check. Low impact, accurate. |
| WP-001 (weak) | insert-headers-and-footers@2.4.0 | `includes/admin/class-wpcode-notifications.php:447` | Kept. Notification dismissal with a nonce and no capability check. |
| WP-001 (weak) | wordpress-seo@28.7 | `admin/ajax/class-yoast-dismissable-notice.php:67` | Kept. Same pattern. |
| WP-001 (weak) | wpforms-lite@2.0.2.2 | `src/Admin/Challenge.php:671`, `:715` | Kept. Onboarding-challenge state behind `check_admin_referer` with no capability check. |
| WP-003 | updraftplus@1.26.13 | `admin.php:752` | **Kept as a true pattern, likely mitigated.** The nopriv restore continuation is gated by `wp_verify_nonce(..., 'updraftplus-credentialtest-nonce')`. Nonces are bound to the user at creation, so a visitor cannot forge one unless a logged-out nonce is printed publicly. Reported because only a nonce stands between an anonymous request and `update_site_option`. |

WP-002: 0 findings. WP-004: all 16 findings weak (the intentionally public routes from Stage 1).

## WP-007: run of 2026-10-21 (13,422 files)

No findings. Known miss: Awesome Support 6.1.5 (the fix for CVE-2023-5355). It uses a realpath + strpos
prefix guard on a variable other than the sink argument. The rule does not recognise that guard, so it
would report the fixed code. The case is kept out of the corpus and recorded in the rule's precision_notes.
