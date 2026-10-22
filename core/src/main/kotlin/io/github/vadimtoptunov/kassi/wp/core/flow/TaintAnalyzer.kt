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
    fun analyze(
        scope: AstNode,
        sinkArguments: (AstNode) -> List<AstNode>,
        initial: Map<String, Taint> = emptyMap(),
    ): List<TaintedSink> = Run(scope, sinkArguments).also { it.env.putAll(initial); it.visit(scope) }.found

    private inner class Run(val scope: AstNode, val sinkArguments: (AstNode) -> List<AstNode>) {
        var env = HashMap<String, Taint>()
        val found = mutableListOf<TaintedSink>()

        /** Source text of expressions proven clean by a guard (`$x`, `$_GET['t']`); a multiset for nesting. */
        var guarded = mutableListOf<String>()

        /** The state on one control-flow path. */
        inner class PathState(val env: HashMap<String, Taint>, val guarded: MutableList<String>)

        fun snapshot() = PathState(HashMap(env), guarded.toMutableList())

        fun restore(p: PathState) {
            env = HashMap(p.env)
            guarded = p.guarded.toMutableList()
        }

        /** Runs [block] from [start] (plus [extraGuards]) and returns the state at its end. */
        fun branch(start: PathState, extraGuards: List<String>, block: () -> Unit): PathState {
            restore(start)
            guarded += extraGuards
            block()
            return snapshot()
        }

        /**
         * May-taint join: a variable is tainted after the `if` when it is tainted, and not guarded, on
         * at least one path that reaches past it. Guards survive only if every such path has them.
         */
        fun join(paths: List<PathState>, fallback: PathState) {
            if (paths.isEmpty()) { restore(fallback); return }
            val joined = HashMap<String, Taint>()
            for (p in paths) for ((name, t) in p.env) {
                if (normalize("\$$name") in p.guarded) continue
                joined[name] = t + joined[name]
            }
            // An element is clean after the join only if every path made it clean.
            joined.keys.filter { '[' in it }.forEach { k -> if (paths.any { k !in it.env }) joined.remove(k) }
            env = joined
            guarded = paths.map { it.guarded.toSet() }.reduce { a, b -> a intersect b }.toMutableList()
        }

        fun visit(n: AstNode) {
            if (n != scope && n.kind.isFunctionLike) return
            when (n.kind) {
                AstKind.ASSIGNMENT, AstKind.CONCAT_ASSIGNMENT -> {
                    val value = n.child(AstRole.VALUE)
                    value?.let(::visit) // sinks inside the value see the state before the write
                    val target = n.child(AstRole.TARGET)
                    if (target != null) {
                        val t = value?.let(::eval)
                        assign(target, if (n.kind == AstKind.CONCAT_ASSIGNMENT) Taint.union(listOf(eval(target), t)) else t, value)
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
                    // foreach ($a as $k => $v) { $a[$k] = esc_attr($v); }: the loop cleans every element.
                    val arr = iterated?.takeIf { it.kind == AstKind.VARIABLE }?.name
                    val k = key?.takeIf { it.kind == AstKind.VARIABLE }?.name
                    if (arr != null && k != null && cleansEveryElement(n, arr, k)) {
                        env.remove(arr)
                        env.keys.removeAll { it.startsWith(elementPrefix(arr)) }
                    }
                }
                AstKind.IF -> visitIf(n)
                else -> n.children.forEach(::visit)
            }
            for (arg in sinkArguments(n)) {
                eval(arg)?.let { found += TaintedSink(n, arg, it) }
            }
        }

        /**
         * `if / elseif / else` with per-path state. The then-branch sees the condition's positive guards;
         * the implicit fall-through path (no `else`) sees its negative guards, so
         * `if (!in_array($x, $ok)) { $x = 'default'; }` and `if (!in_array($x, $ok)) { return; }` both
         * leave `$x` clean. Paths that end in return/exit/die/a terminator do not reach the join.
         */
        fun visitIf(n: AstNode) {
            val condition = n.child(AstRole.CONDITION)
            val body = n.child(AstRole.BODY)
            condition?.let(::visit)
            val start = snapshot()
            val reaching = mutableListOf<PathState>()

            val thenPath = branch(start, condition?.let(::positiveGuards).orEmpty()) { body?.let(::visit) }
            if (body == null || !terminates(body)) reaching += thenPath

            val others = n.children.filter { it != condition && it != body }
            var hasElse = false
            for (b in others) {
                val isElse = b.text.startsWith("else") && !b.text.startsWith("elseif")
                hasElse = hasElse || isElse
                val p = branch(start, emptyList()) { visit(b) }
                val inner = b.children.lastOrNull()
                if (inner == null || !terminates(inner)) reaching += p
            }
            if (!hasElse) reaching += branch(start, condition?.let(::negativeGuards).orEmpty()) {}
            join(reaching, start)
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

        /** Variables holding one `$_FILES` entry (`$f = $_FILES['image']`). */
        val filesAliases = mutableSetOf<String>()

        fun assign(target: AstNode, t: Taint?) = assign(target, t, null)

        fun assign(target: AstNode, t: Taint?, value: AstNode?) {
            if (target.kind == AstKind.VARIABLE) target.name?.let { name ->
                if (value?.kind == AstKind.ARRAY_ACCESS && rootVariable(value) == "_FILES") filesAliases += name else filesAliases -= name
            }
            // A new value replaces whatever a guard proved about the old one.
            guarded.removeAll { it == normalize(target.text) }
            when (target.kind) {
                AstKind.VARIABLE -> target.name?.let { name ->
                    env.keys.removeAll { it.startsWith(elementPrefix(name)) }
                    if (t == null) env.remove(name) else env[name] = t
                }
                AstKind.ARRAY_ACCESS -> rootVariable(target)?.let { name ->
                    // $a['k'] = v: a strong update of that element ...
                    elementKey(target)?.let { env[it] = t ?: CLEAN }
                    // ... and a weak update of the whole array (for reads without a literal key).
                    t?.let { env[name] = it + env[name] }
                }
                else -> Unit // properties, list() destructuring: not tracked
            }
        }

        fun cleansEveryElement(loop: AstNode, arr: String, key: String): Boolean = loop.walk().any { a ->
            a.kind == AstKind.ASSIGNMENT && a.child(AstRole.TARGET)?.let { t ->
                t.kind == AstKind.ARRAY_ACCESS && t.child(AstRole.RECEIVER)?.name == arr && t.child(AstRole.KEY)?.name == key
            } == true && a.child(AstRole.VALUE)?.let { v -> v.kind == AstKind.CALL && v.name?.lowercase() in spec.sanitizerFunctions } == true
        }

        fun elementPrefix(name: String) = "$name["

        /** `$a['k']` on a plain variable with a literal key → "a['k']"; otherwise null. */
        fun elementKey(n: AstNode): String? {
            val recv = n.child(AstRole.RECEIVER)?.takeIf { it.kind == AstKind.VARIABLE }?.name ?: return null
            val key = n.child(AstRole.KEY)?.takeIf { it.kind == AstKind.STRING_LITERAL || it.kind == AstKind.NUMBER_LITERAL } ?: return null
            return elementPrefix(recv) + (key.stringValue ?: key.text) + "]"
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
            // clean_keys apply to $_FILES entries only ($_FILES['f']['tmp_name'], or $f['tmp_name'] after
            // $f = $_FILES['f']), never to e.g. $_GET['name'].
            AstKind.ARRAY_ACCESS -> if (rootVariable(n).let { it == "_FILES" || it in filesAliases } &&
                n.child(AstRole.KEY)?.stringValue?.let { it in spec.cleanKeys } == true
            ) null else {
                val receiver = n.child(AstRole.RECEIVER)
                val global = receiver?.takeIf { it.kind == AstKind.VARIABLE }?.name?.takeIf(::isSuperglobal)
                val element = elementKey(n)?.let { env[it] }
                when {
                    global != null -> superglobal(global, n.child(AstRole.KEY)?.stringValue, keyed = true)
                    element != null -> element.takeIf { it.sources.isNotEmpty() }
                    else -> receiver?.let(::eval)
                }
            }
            AstKind.INTERPOLATED_STRING -> if (spec.hostPrefixCleans && HOST_PREFIX.containsMatchIn(n.stringValue.orEmpty())) null else {
                Taint.union(n.children.map(::eval))
            }
            AstKind.CONCAT -> if (spec.hostPrefixCleans && hostFixed(n)) null else {
                Taint.union(listOf(n.child(AstRole.LEFT), n.child(AstRole.RIGHT)).map { it?.let(::eval) })
            }
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
                // array_map('esc_attr', $atts): every element goes through a sanitizer.
                name == "array_map" && n.arguments.firstOrNull()?.let { cb ->
                    cb.kind == AstKind.STRING_LITERAL && cb.stringValue?.lowercase() in spec.sanitizerFunctions
                } == true -> null
                name == "array_map" -> n.arguments.drop(1).let { Taint.union(it.map(::eval)) }
                spec.sources.any { it is SourceSpec.Function && it.name == name } -> Taint(setOf("$name()"))
                name in spec.formatFunctions -> formatTaint(n)
                name in spec.passthrough -> Taint.union(n.arguments.map(::eval))
                else -> null
            }
        }

        /**
         * URL rules (`params.host_prefix_cleans`): input appended after a fixed scheme and host cannot
         * change where the request goes. Fixed means a literal `scheme://host/…` (or `?`/`#`), or a
         * constant / class constant / clean value as the leftmost operand.
         */
        fun hostFixed(concat: AstNode): Boolean {
            var left: AstNode = concat
            while (left.kind == AstKind.CONCAT) left = left.child(AstRole.LEFT) ?: return false
            return when (left.kind) {
                AstKind.STRING_LITERAL, AstKind.INTERPOLATED_STRING -> HOST_PREFIX.containsMatchIn(left.stringValue.orEmpty())
                AstKind.CONSTANT -> left.name?.lowercase() !in setOf("true", "false", "null")
                AstKind.CLASS_CONSTANT -> true
                AstKind.CALL, AstKind.STATIC_CALL, AstKind.METHOD_CALL, AstKind.PROPERTY_ACCESS -> eval(left) == null
                else -> false
            }
        }

        /** sprintf-like: only arguments consumed by a `%s` placeholder carry taint into the result. */
        fun formatTaint(call: AstNode): Taint? {
            val format = call.arguments.firstOrNull() ?: return null
            val args = call.arguments.drop(1)
            val literal = format.stringValue?.takeIf { format.kind == AstKind.STRING_LITERAL }
                ?: return Taint.union(call.arguments.map(::eval))
            if (spec.hostPrefixCleans) {
                if (HOST_PREFIX.containsMatchIn(literal)) return null
                // '%s/path?...' whose first %s is a clean base URL
                if (literal.startsWith("%s/") && args.firstOrNull()?.let(::eval) == null) return null
            }
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
        private val CLEAN = Taint(emptySet())
        private val HOST_PREFIX = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://[^/?#'"\s]+[/?#]""")
        private val COMPARISONS = setOf("==", "===", "!=", "!==", "<>")
        private val CLEAN_CASTS = setOf("int", "integer", "float", "double", "bool", "boolean")
        // %[argnum$][flags][width][.precision]specifier, plus %%.
        private val PLACEHOLDER = Regex("""%%|%(?:(\d+)\$)?[-+ 0]*(?:'.)?\d*(?:\.\d+)?([bcdeEfFgGosuxX])""")
    }
}
