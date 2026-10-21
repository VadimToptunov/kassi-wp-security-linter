package io.github.vadimtoptunov.kassi.wp.core.hooks

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.ancestors
import io.github.vadimtoptunov.kassi.wp.core.ast.walk

/**
 * Same-file resolution of PHP callables, the only kind v0.1 follows. Returns the function, method or
 * closure node, or null when the target is in another file, dynamic, or ambiguous.
 */
class Callables(private val file: AstNode) {
    private val functions: Map<String, List<AstNode>> by lazy {
        file.walk().filter { it.kind == AstKind.FUNCTION && it.name != null }.groupBy { it.name!!.lowercase() }
    }
    private val classes: Map<String, List<AstNode>> by lazy {
        file.walk().filter { it.kind == AstKind.CLASS && it.name != null }.groupBy { it.name!!.lowercase() }
    }

    /** Resolves a callback expression as written at [site] (used for `$this` / `self` / `__CLASS__`). */
    fun resolve(callback: AstNode, site: AstNode): AstNode? = when (callback.kind) {
        AstKind.CLOSURE -> callback
        AstKind.STRING_LITERAL -> callback.stringValue?.let { s ->
            val parts = s.trimStart('\\').split("::")
            when (parts.size) {
                1 -> function(parts[0])
                2 -> method(classes[parts[0].substringAfterLast('\\').lowercase()]?.singleOrNull(), parts[1])
                else -> null
            }
        }
        AstKind.ARRAY_LITERAL -> {
            val entries = callback.arguments.map { it.child(AstRole.VALUE) }
            val target = entries.getOrNull(0)
            val name = entries.getOrNull(1)?.takeIf { it.kind == AstKind.STRING_LITERAL }?.stringValue
            if (entries.size != 2 || target == null || name == null) null else method(classOf(target, site), name)
        }
        else -> null
    }

    /** A call inside [scope] to a same-file function, or to a method of the enclosing class via `$this`/`self`/`static`. */
    fun resolveCall(call: AstNode): AstNode? {
        val name = call.name ?: return null
        return when (call.kind) {
            AstKind.CALL -> function(name)
            AstKind.METHOD_CALL, AstKind.STATIC_CALL -> {
                val recv = call.child(AstRole.RECEIVER) ?: return null
                val own = (recv.kind == AstKind.VARIABLE && recv.name == "this") ||
                    (recv.kind == AstKind.CLASS_REFERENCE && recv.name?.lowercase() in SELF_NAMES)
                if (own) method(enclosingClass(call), name) else null
            }
            else -> null
        }
    }

    private fun function(name: String): AstNode? = functions[name.trimStart('\\').lowercase()]?.singleOrNull()

    private fun method(cls: AstNode?, name: String): AstNode? =
        cls?.walk()?.filter { it.kind == AstKind.METHOD && it.name.equals(name, ignoreCase = true) }?.toList()?.singleOrNull()

    private fun enclosingClass(n: AstNode): AstNode? = n.ancestors().firstOrNull { it.kind == AstKind.CLASS }

    /** `$this`, `__CLASS__`, `self::class`, `static::class`, `'ClassName'`, `ClassName::class`. */
    private fun classOf(target: AstNode, site: AstNode): AstNode? = when {
        target.kind == AstKind.VARIABLE && target.name == "this" -> enclosingClass(site)
        target.kind == AstKind.CONSTANT && target.name == "__CLASS__" -> enclosingClass(site)
        target.kind == AstKind.CLASS_CONSTANT && target.name.equals("class", ignoreCase = true) -> {
            val ref = target.child(AstRole.RECEIVER)?.name?.lowercase()
            if (ref in SELF_NAMES) enclosingClass(site) else ref?.let { classes[it]?.singleOrNull() }
        }
        target.kind == AstKind.STRING_LITERAL ->
            target.stringValue?.substringAfterLast('\\')?.lowercase()?.let { classes[it]?.singleOrNull() }
        else -> null
    }

    companion object {
        private val SELF_NAMES = setOf("self", "static")
    }
}
