package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-012: request input decides a user's role or capabilities.
 *
 * Sinks (all from params):
 *  - `role_methods`: `->set_role($r)`, `->add_role($r)`, `->add_cap($c)`: the first argument;
 *  - `user_functions`: `wp_insert_user($args)` / `wp_update_user($args)`: the `'role'` entry of a
 *    literal args array, or the whole args when it is not a literal (`wp_update_user($_POST)`);
 *  - `meta_functions`: `update_user_meta($id, <key containing "capabilities">, $value)`: the value.
 */
class PrivilegeEscalationCheck(rule: Rule) : TaintCheck(rule) {
    private val roleMethods = rule.params.strings("role_methods").map { it.lowercase() }.toSet()
    private val userFunctions = rule.params.strings("user_functions").map { it.lowercase() }.toSet()
    private val metaFunctions = rule.params.strings("meta_functions").map { it.lowercase() }.toSet()
    private val roleKeys = rule.params.strings("role_keys").toSet()

    override fun sinkArguments(node: AstNode): List<AstNode> {
        val name = node.name?.lowercase() ?: return emptyList()
        return when {
            node.kind == AstKind.METHOD_CALL && name in roleMethods -> listOfNotNull(node.arguments.firstOrNull())
            node.kind == AstKind.CALL && name in userFunctions -> {
                val args = node.arguments.firstOrNull() ?: return emptyList()
                if (args.kind == AstKind.ARRAY_LITERAL) {
                    args.arguments.filter { it.child(AstRole.KEY)?.stringValue in roleKeys }.mapNotNull { it.child(AstRole.VALUE) }
                } else {
                    listOf(args)
                }
            }
            node.kind == AstKind.CALL && name in metaFunctions -> {
                val key = node.arguments.getOrNull(1)
                if (key != null && "capabilities" in key.text.lowercase()) listOfNotNull(node.arguments.getOrNull(2)) else emptyList()
            }
            else -> emptyList()
        }
    }

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) decides a user's role or capabilities in ${sink.name}(): privilege escalation. " +
            "Use a fixed role or an allow-list"
}
