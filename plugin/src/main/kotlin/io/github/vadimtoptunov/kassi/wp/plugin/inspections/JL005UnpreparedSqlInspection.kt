package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** JL-005 (CWE-89): request input in a Joomla `$db->setQuery()` query without `$db->quote()/escape()`. Logic: core `SqlInjectionCheck`. */
class JL005UnpreparedSqlInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "JL-005"
    }
}
