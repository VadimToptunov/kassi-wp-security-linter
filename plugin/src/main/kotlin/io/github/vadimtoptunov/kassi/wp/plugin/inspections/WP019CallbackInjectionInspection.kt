package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** WP-019 (CWE-94): request data reaches the callable of call_user_func()/call_user_func_array(), i.e. arbitrary function invocation. Logic: core `CallbackInjectionCheck`. */
class WP019CallbackInjectionInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "WP-019"
    }
}
