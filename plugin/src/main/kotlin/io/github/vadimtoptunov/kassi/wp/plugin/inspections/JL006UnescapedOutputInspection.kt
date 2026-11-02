package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** JL-006 (CWE-79): Joomla request input echoed without htmlspecialchars()/$this->escape(). Logic: core `UnescapedOutputCheck`. */
class JL006UnescapedOutputInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "JL-006"
    }
}
