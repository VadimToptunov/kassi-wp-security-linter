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
