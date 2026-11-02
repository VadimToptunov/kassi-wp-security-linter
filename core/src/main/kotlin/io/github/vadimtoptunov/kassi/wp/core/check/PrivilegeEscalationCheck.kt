package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.walk
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-012: request input decides a user's role or capabilities.
 *
 * Sinks (all from params):
 *  - `role_methods`: `->set_role($r)`, `->add_role($r)`, `->add_cap($c)`: the first argument;
 *  - `user_functions`: `wp_insert_user($args)` / `wp_update_user($args)`: the `'role'` entry of the
 *    args array (field-level) — resolved whether the array is a literal (`wp_insert_user(['role'=>$r])`)
 *    or built up by key (`$a['role'] = $r; wp_insert_user($a)`) — or the whole args only when they are
 *    request data wholesale (`wp_update_user($_POST)`, `$a = $_POST; wp_update_user($a)`);
 *  - `meta_functions`: `update_user_meta($id, <key containing "capabilities">, $value)`: the value.
 *
 * Field-level resolution matters: a locally-built userdata array routinely carries request data in
 * other entries (`$a['user_url'] = $_GET['website']`) while `role` comes from a fixed option or is
 * absent. Tainting the whole array there is the dominant WP-012 false positive — so for a non-literal
 * args variable we taint only what actually flows into the `role` key.
 */
class PrivilegeEscalationCheck(rule: Rule) : TaintCheck(rule) {
    private val roleMethods = rule.params.strings("role_methods").map { it.lowercase() }.toSet()
    private val userFunctions = rule.params.strings("user_functions").map { it.lowercase() }.toSet()
    private val metaFunctions = rule.params.strings("meta_functions").map { it.lowercase() }.toSet()
    private val roleKeys = rule.params.strings("role_keys").toSet()

    /** PHP superglobals: args that *are* request data, so the attacker controls every entry incl. role. */
    private val superglobals = setOf("_GET", "_POST", "_REQUEST", "_COOKIE", "_SERVER", "_FILES", "_ENV", "GLOBALS")

    override fun sinkArguments(node: AstNode): List<AstNode> {
        val name = node.name?.lowercase() ?: return emptyList()
        return when {
            node.kind == AstKind.METHOD_CALL && name in roleMethods -> listOfNotNull(node.arguments.firstOrNull())
            node.kind == AstKind.CALL && name in userFunctions -> {
                val args = node.arguments.firstOrNull() ?: return emptyList()
                when {
                    args.kind == AstKind.ARRAY_LITERAL ->
                        args.arguments.filter { it.child(AstRole.KEY)?.stringValue in roleKeys }.mapNotNull { it.child(AstRole.VALUE) }
                    // A plain variable: either it carries request data wholesale (flag it all), or it is a
                    // built-up array where only the `role`-key writes decide the privilege (flag those).
                    args.kind == AstKind.VARIABLE ->
                        if (args.name in superglobals || seededFromRequestWholesale(args)) listOf(args)
                        else roleKeyWrites(args)
                    // Any other expression (direct `$_POST['x']` access, a call returning an array, a
                    // ternary): keep the conservative whole-args behaviour.
                    else -> listOf(args)
                }
            }
            node.kind == AstKind.CALL && name in metaFunctions -> {
                val key = node.arguments.getOrNull(1)
                if (key != null && "capabilities" in key.text.lowercase()) listOfNotNull(node.arguments.getOrNull(2)) else emptyList()
            }
            else -> emptyList()
        }
    }

    /** The enclosing function / method / closure of [node], else its file (top-level code). */
    private fun enclosingScope(node: AstNode): AstNode {
        var cur: AstNode? = node.parent
        while (cur != null) {
            if (cur.kind.isFunctionLike || cur.kind == AstKind.FILE) return cur
            cur = cur.parent
        }
        return node
    }

    /**
     * The values written to a `role` key of the array variable [v] within its scope:
     * `$v['role'] = <value>` (any key in [roleKeys]). These are the only entries that can change the
     * created/updated user's role, so they — not the whole array — are the sinks for a built-up array.
     */
    private fun roleKeyWrites(v: AstNode): List<AstNode> {
        val varName = v.name ?: return emptyList()
        return enclosingScope(v).walk()
            .filter { it.kind == AstKind.ASSIGNMENT }
            .mapNotNull { assign ->
                val target = assign.child(AstRole.TARGET) ?: return@mapNotNull null
                if (target.kind != AstKind.ARRAY_ACCESS) return@mapNotNull null
                val recv = target.child(AstRole.RECEIVER)
                if (recv?.kind != AstKind.VARIABLE || recv.name != varName) return@mapNotNull null
                if (target.child(AstRole.KEY)?.stringValue !in roleKeys) return@mapNotNull null
                assign.child(AstRole.VALUE)
            }
            .toList()
    }

    /**
     * True when [v] is assigned request data wholesale somewhere in its scope — `$v = $_POST`,
     * `$v = wp_unslash($_REQUEST)` — so the attacker controls every entry, `role` included, even
     * without an explicit `$v['role']` write. A seed from an array literal (`$v = []`) does not count.
     */
    private fun seededFromRequestWholesale(v: AstNode): Boolean {
        val varName = v.name ?: return false
        return enclosingScope(v).walk()
            .filter { it.kind == AstKind.ASSIGNMENT }
            .any { assign ->
                val target = assign.child(AstRole.TARGET)
                if (target?.kind != AstKind.VARIABLE || target.name != varName) return@any false
                val value = assign.child(AstRole.VALUE) ?: return@any false
                // `$v = array(... $_POST['x'] ...)` builds an array with a request *entry* — not
                // wholesale request data — so the attacker does not control an unwritten `role` key.
                // Only a non-array seed that carries a superglobal (`$v = $_POST`, `$v = wp_unslash($_REQUEST)`)
                // counts as wholesale.
                if (value.kind == AstKind.ARRAY_LITERAL) return@any false
                value.walk().any { it.kind == AstKind.VARIABLE && it.name in superglobals }
            }
    }

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) decides a user's role or capabilities in ${sink.name}(): privilege escalation. " +
            "Use a fixed role or an allow-list"
}
