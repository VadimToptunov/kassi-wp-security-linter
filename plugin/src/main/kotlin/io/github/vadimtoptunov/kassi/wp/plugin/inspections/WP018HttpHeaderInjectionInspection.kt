package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** WP-018 (CWE-113): request data reaches PHP header() without CR/LF removal, i.e. HTTP response header injection / splitting. Logic: core `HttpHeaderInjectionCheck`. */
class WP018HttpHeaderInjectionInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "WP-018"
    }
}
