package io.github.vadimtoptunov.kassi.wp.core.flow

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.walk

/** A sink argument that untrusted input provably reaches. */
data class TaintedSink(val sink: AstNode, val argument: AstNode, val taint: Taint)

/**
 * Lightweight intra-procedural taint: source → (sanitizer?) → sink within one scope (a function,
 * method, closure, or the file's top level), along assignments, `.=`, concatenation, interpolation,
 * array element reads/writes, `foreach`, `??`/ternaries and passthrough functions.
 *
 * Deliberately simple and biased to silence:
 *  - statements are processed in source order with strong updates (`$x = intval($x)` cleans `$x`);
 *    branches and loops are not joined, so a conditional re-assignment can only hide a flow, never invent one;
 *  - anything not proven tainted is clean: parameters, properties, unknown function results,
 *    closure `use` variables, globals;
 *  - nested functions/closures are separate scopes and see nothing from the enclosing one;
 *  - allow-list guards clean their argument: inside `if (in_array($x, $allowed))`, and after
 *    `if (!in_array($x, $allowed)) { return; }` (guards and terminators come from the rule).
 *
 * @param callOverride rule-specific semantics for a call (return null to fall back to the defaults).
 *   It gets the call node and an evaluator for argument expressions. The return value is the
 *   call's taint wrapped in [Override], so "clean" and "no override" can be told apart.
 */
class TaintAnalyzer(
    private val spec: TaintSpec,
    private val callOverride: (call: AstNode, eval: (AstNode) -> Taint?) -> Override? = { _, _ -> null },
) {
    data class Override(val taint: Taint?)

    /** Every scope of [file]: the file's top level plus each function, method and closure. */
    fun scopes(file: AstNode): List<AstNode> = listOf(file) + file.walk().drop(1).filter { it.kind.isFunctionLike }.toList()

    /**
     * Analyses one [scope]. [sinkArguments] is asked for every node, in evaluation order, and returns
     * the argument nodes of that node that are sinks (empty for non-sinks).
     */
    fun analyze(scope: AstNode, sinkArguments: (AstNode) -> List<AstNode>): List<TaintedSink> =
        Run(scope, sinkArguments).also { it.visit(scope) }.found

    private inner class Run(val scope: AstNode, val sinkArguments: (AstNode) -> List<AstNode>) {
        val env = HashMap<String, Taint>()
        val found = mutableListOf<TaintedSink>()

        /** Source text of expressions proven clean by a guard (`$x`, `$_GET['t']`); a multiset for nesting. */
        val guarded = mutableListOf<String>()

        fun visit(n: AstNode) {
            if (n != scope && n.kind.isFunctionLike) return
            when (n.kind) {
                AstKind.ASSIGNMENT, AstKind.CONCAT_ASSIGNMENT -> {
                    val value = n.child(AstRole.VALUE)
                    value?.let(::visit) // sinks inside the value see the state before the write
                    val target = n.child(AstRole.TARGET)
                    if (target != null) {
                        val t = value?.let(::eval)
                        assign(target, if (n.kind == AstKind.CONCAT_ASSIGNMENT) Taint.union(listOf(eval(target), t)) else t)
                    }
                }
                AstKind.FOREACH -> {
                    val iterated = n.child(AstRole.VALUE)
                    val key = n.child(AstRole.KEY)
                    val target = n.child(AstRole.TARGET)
                    iterated?.let(::visit)
                    val t = iterated?.let(::eval)
                    // Keys of an input array are attacker-controlled too.
                    key?.let { assign(it, t) }
                    target?.let { assign(it, t) }
                    n.children.filter { it != iterated && it != key && it != target }.forEach(::visit)
                }
                AstKind.IF -> visitIf(n)
                else -> n.children.forEach(::visit)
            }
            for (arg in sinkArguments(n)) {
                eval(arg)?.let { found += TaintedSink(n, arg, it) }
            }
        }

        fun visitIf(n: AstNode) {
            val condition = n.child(AstRole.CONDITION)
            val body = n.child(AstRole.BODY)
            condition?.let(::visit)
            val inside = condition?.let(::positiveGuards).orEmpty()
            guarded += inside
            body?.let(::visit)
            inside.forEach { guarded.remove(it) }
            n.children.filter { it != condition && it != body }.forEach(::visit) // elseif / else
            // if (!guard($x)) { return; } → $x is clean from here on.
            if (condition != null && body != null && terminates(body)) guarded += negativeGuards(condition)
        }

        fun guardArgument(n: AstNode): String? =
            n.takeIf { it.kind == AstKind.CALL && it.name?.lowercase() in spec.guardFunctions }
                ?.arguments?.firstOrNull()
                ?.takeIf { it.kind == AstKind.VARIABLE || it.kind == AstKind.ARRAY_ACCESS }
                ?.let { normalize(it.text) }

        /** `validate_file($f) === 0`, `0 !== validate_file($f)`: a comparison on a guard call is a check. */
        fun comparedGuard(c: AstNode): String? =
            if (c.kind == AstKind.BINARY && c.name in COMPARISONS) {
                listOfNotNull(c.child(AstRole.LEFT), c.child(AstRole.RIGHT)).firstNotNullOfOrNull(::guardArgument)
            } else {
                null
            }

        fun positiveGuards(c: AstNode): List<String> = when {
            comparedGuard(c) != null -> listOf(comparedGuard(c)!!)
            c.kind == AstKind.BINARY && (c.name == "&&" || c.name.equals("and", ignoreCase = true)) ->
                listOfNotNull(c.child(AstRole.LEFT), c.child(AstRole.RIGHT)).flatMap(::positiveGuards)
            c.kind == AstKind.OTHER && c.text.startsWith("(") -> c.children.singleOrNull()?.let(::positiveGuards).orEmpty()
            else -> listOfNotNull(guardArgument(c))
        }

        fun negativeGuards(c: AstNode): List<String> = when {
            comparedGuard(c) != null -> listOf(comparedGuard(c)!!)
            c.kind == AstKind.BINARY && (c.name == "||" || c.name.equals("or", ignoreCase = true)) ->
                listOfNotNull(c.child(AstRole.LEFT), c.child(AstRole.RIGHT)).flatMap(::negativeGuards)
            c.kind == AstKind.OTHER && c.text.startsWith("!") -> c.children.singleOrNull()?.let(::positiveGuards).orEmpty()
            c.kind == AstKind.OTHER && c.text.startsWith("(") -> c.children.singleOrNull()?.let(::negativeGuards).orEmpty()
            else -> emptyList()
        }

        /** The branch always leaves the request: ends in return, exit/die, or a terminator call. */
        fun terminates(body: AstNode): Boolean {
            val last = if (body.kind == AstKind.BLOCK) body.children.lastOrNull() else body
            last ?: return false
            val stmt = if (last.kind == AstKind.OTHER && last.children.size == 1 && !last.text.startsWith("exit") && !last.text.startsWith("die")) last.children.single() else last
            return stmt.kind == AstKind.RETURN ||
                stmt.text.startsWith("exit") || stmt.text.startsWith("die") ||
                (stmt.kind == AstKind.CALL && stmt.name?.lowercase() in spec.terminators)
        }

        fun normalize(text: String) = text.filterNot { it.isWhitespace() }.replace('"', '\'')

        fun assign(target: AstNode, t: Taint?) {
            // A new value replaces whatever a guard proved about the old one.
            guarded.removeAll { it == normalize(target.text) }
            when (target.kind) {
                AstKind.VARIABLE -> target.name?.let { if (t == null) env.remove(it) else env[it] = t }
                // $a['k'] = ... / $a[] = ...: weak update of the whole array.
                AstKind.ARRAY_ACCESS -> rootVariable(target)?.let { name -> t?.let { env[name] = it + env[name] } }
                else -> Unit // properties, list() destructuring: not tracked
            }
        }

        fun rootVariable(n: AstNode): String? = when (n.kind) {
            AstKind.VARIABLE -> n.name
            AstKind.ARRAY_ACCESS -> n.child(AstRole.RECEIVER)?.let(::rootVariable)
            else -> null
        }

        fun eval(n: AstNode): Taint? = when {
            (n.kind == AstKind.VARIABLE || n.kind == AstKind.ARRAY_ACCESS) && normalize(n.text) in guarded -> null
            else -> evalUnguarded(n)
        }

        fun evalUnguarded(n: AstNode): Taint? = when (n.kind) {
            AstKind.VARIABLE -> n.name?.let { name -> superglobal(name, null, keyed = false) ?: env[name] }
            AstKind.ARRAY_ACCESS -> if (n.child(AstRole.KEY)?.stringValue?.let { it in spec.cleanKeys } == true) null else {
                val receiver = n.child(AstRole.RECEIVER)
                val global = receiver?.takeIf { it.kind == AstKind.VARIABLE }?.name?.takeIf(::isSuperglobal)
                if (global != null) {
                    superglobal(global, n.child(AstRole.KEY)?.stringValue, keyed = true)
                } else {
                    receiver?.let(::eval)
                }
            }
            AstKind.INTERPOLATED_STRING -> Taint.union(n.children.map(::eval))
            AstKind.CONCAT -> Taint.union(listOf(n.child(AstRole.LEFT), n.child(AstRole.RIGHT)).map { it?.let(::eval) })
            AstKind.BINARY -> if (n.name == "??") Taint.union(listOf(n.child(AstRole.LEFT), n.child(AstRole.RIGHT)).map { it?.let(::eval) }) else null
            AstKind.TERNARY -> Taint.union(
                listOf(n.child(AstRole.LEFT) ?: n.child(AstRole.CONDITION), n.child(AstRole.RIGHT)).map { it?.let(::eval) },
            )
            AstKind.CAST -> if (n.name in CLEAN_CASTS) null else n.child(AstRole.VALUE)?.let(::eval)
            AstKind.ASSIGNMENT -> n.child(AstRole.VALUE)?.let(::eval)
            AstKind.CALL -> evalCall(n)
            AstKind.METHOD_CALL -> {
                val name = n.name?.lowercase()
                when {
                    name == null || name in spec.sanitizerMethods -> null
                    spec.sources.any { it is SourceSpec.Method && it.name == name } -> Taint(setOf("->$name()"))
                    else -> null
                }
            }
            // `($x)` and `@$x` are transparent; any other unmapped construct is not proven.
            AstKind.OTHER -> n.children.singleOrNull()?.takeIf { n.text.startsWith("(") || n.text.startsWith("@") }?.let(::eval)
            else -> null
        }

        fun evalCall(n: AstNode): Taint? {
            callOverride(n, ::eval)?.let { return it.taint }
            val name = n.name?.lowercase() ?: return null
            return when {
                name in spec.sanitizerFunctions -> null
                spec.sources.any { it is SourceSpec.Function && it.name == name } -> Taint(setOf("$name()"))
                name in spec.formatFunctions -> formatTaint(n)
                name in spec.passthrough -> Taint.union(n.arguments.map(::eval))
                else -> null
            }
        }

        /** sprintf-like: only arguments consumed by a `%s` placeholder carry taint into the result. */
        fun formatTaint(call: AstNode): Taint? {
            val format = call.arguments.firstOrNull() ?: return null
            val args = call.arguments.drop(1)
            val literal = format.stringValue?.takeIf { format.kind == AstKind.STRING_LITERAL }
                ?: return Taint.union(call.arguments.map(::eval))
            var next = 0
            val used = mutableListOf<AstNode>()
            for (m in PLACEHOLDER.findAll(literal)) {
                if (m.value == "%%") continue
                val index = m.groups[1]?.value?.toInt()?.minus(1) ?: next++
                if (m.groups[2]!!.value == "s") args.getOrNull(index)?.let(used::add)
            }
            return Taint.union(used.map(::eval))
        }

        fun isSuperglobal(name: String) = spec.sources.any { it is SourceSpec.Superglobal && it.name == name }

        /** [keyed]: the superglobal is read through `[key]` ([key] null when the key is not a literal). */
        fun superglobal(name: String, key: String?, keyed: Boolean): Taint? {
            val specs = spec.sources.filterIsInstance<SourceSpec.Superglobal>().filter { it.name == name }
            if (specs.isEmpty()) return null
            val hit = specs.any { s -> if (keyed) s.matchesKey(key) else s.keyPattern == null }
            return if (hit) Taint(setOf("\$$name")) else null
        }
    }

    companion object {
        private val COMPARISONS = setOf("==", "===", "!=", "!==", "<>")
        private val CLEAN_CASTS = setOf("int", "integer", "float", "double", "bool", "boolean")
        // %[argnum$][flags][width][.precision]specifier, plus %%.
        private val PLACEHOLDER = Regex("""%%|%(?:(\d+)\$)?[-+ 0]*(?:'.)?\d*(?:\.\d+)?([bcdeEfFgGosuxX])""")
    }
}
