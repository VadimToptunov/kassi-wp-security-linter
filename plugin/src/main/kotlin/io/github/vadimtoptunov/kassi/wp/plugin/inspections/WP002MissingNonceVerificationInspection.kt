package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-002 (CWE-352 handler changes state without nonce verification). Logic: core `HookHandlerCheck`. */
class WP002MissingNonceVerificationInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-002"
    }
}
