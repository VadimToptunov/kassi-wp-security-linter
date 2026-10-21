package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** WP-005 (CWE-89): request data in `$wpdb` SQL without `prepare()`. Logic: core `SqlInjectionCheck`. */
class WP005UnpreparedSqlInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "WP-005"
    }
}
