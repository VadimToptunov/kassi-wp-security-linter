package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import io.github.vadimtoptunov.kassi.wp.core.check.SqlInjectionCheck
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/** WP-005 (CWE-89): request data in `$wpdb` SQL without `prepare()`. Logic: [SqlInjectionCheck]. */
class WP005UnpreparedSqlInspection : TaintInspection(RULE_ID) {
    override fun createCheck(rule: Rule) = SqlInjectionCheck(rule)

    companion object {
        const val RULE_ID = "WP-005"
    }
}
