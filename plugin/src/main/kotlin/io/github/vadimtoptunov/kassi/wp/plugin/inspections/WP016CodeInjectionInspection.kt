package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** WP-016 (CWE-94): request data reaches eval()/assert()/create_function() and is executed as PHP. Logic: core `CodeInjectionCheck`. */
class WP016CodeInjectionInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "WP-016"
    }
}
