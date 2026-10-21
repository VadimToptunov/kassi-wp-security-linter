package io.github.vadimtoptunov.kassi.wp.core.ast

/**
 * Parser-neutral view of a PHP syntax tree. Everything in `core` (the knowledge base, the taint
 * flow, the rule checks) runs over this interface, never over a concrete parser's types.
 *
 * Adapters implement it: `plugin` wraps IntelliJ PHP PSI; other tools can wrap a headless
 * PHP parser. An adapter maps what it understands to an [AstKind] and everything else to
 * [AstKind.OTHER] — keeping its children, so analysis can still descend through unknown nodes.
 *
 * Contract for adapters:
 *  - [children] are in source order and exclude whitespace and comments.
 *  - [name] is the bare identifier, without `$` for variables and without a leading `\` for
 *    function/constant names (`_GET`, `wp_redirect`, `ABSPATH`). Null when the node has none or the
 *    name is dynamic (`$$x`, `$f()`).
 *  - [arguments] is non-empty only for call-like kinds ([AstKind.isCall]) and [AstKind.ECHO] /
 *    [AstKind.PRINT] / [AstKind.INCLUDE] / [AstKind.ARRAY_LITERAL] (its entries).
 *  - Node identity: two wrappers of the same underlying node are `equals`.
 */
interface AstNode {
    val kind: AstKind
    val name: String?
    /** Exact source text of the node. */
    val text: String
    val location: SourceLocation
    val parent: AstNode?
    val children: List<AstNode>
    val arguments: List<AstNode>

    /** The child playing [role], or null. See [AstRole] for which kinds define which roles. */
    fun child(role: AstRole): AstNode?

    /**
     * For [AstKind.STRING_LITERAL] / [AstKind.INTERPOLATED_STRING]: the literal value with quotes
     * removed (interpolated parts are left as source text). Null for every other kind.
     */
    val stringValue: String?
}

enum class AstKind {
    FILE,
    /**
     * Named function declaration. [AstNode.name] = function name; body under [AstRole.BODY]
     * (a [BLOCK]; for an arrow function `fn () => expr`, the expression itself).
     */
    FUNCTION,
    /** Class method declaration. [AstNode.name] = method name. */
    METHOD,
    /** `function () {}` or `fn () =>`. */
    CLOSURE,
    /** `foo(...)`. [AstNode.name] = callee name, null when dynamic. */
    CALL,
    /** `$obj->foo(...)`. [AstNode.name] = method name; [AstRole.RECEIVER] = `$obj`. */
    METHOD_CALL,
    /** `Foo::bar(...)`. [AstNode.name] = method name; [AstRole.RECEIVER] = class reference. */
    STATIC_CALL,
    /** `new Foo(...)`. [AstNode.name] = class name. */
    NEW,
    /** `$x`. [AstNode.name] = `x` (superglobals: `_GET`, `_POST`, ...). */
    VARIABLE,
    /** `$a[k]`. [AstRole.RECEIVER] = `$a`, [AstRole.KEY] = `k` (null for `$a[]`). */
    ARRAY_ACCESS,
    /** `$obj->prop`. [AstNode.name] = property; [AstRole.RECEIVER] = `$obj`. */
    PROPERTY_ACCESS,
    /** `=` (also `??=` etc.). [AstRole.TARGET], [AstRole.VALUE]. */
    ASSIGNMENT,
    /** `.=`. [AstRole.TARGET], [AstRole.VALUE]. */
    CONCAT_ASSIGNMENT,
    /** `a . b`. [AstRole.LEFT], [AstRole.RIGHT]. */
    CONCAT,
    /** Any other binary operator. [AstRole.LEFT], [AstRole.RIGHT]; [AstNode.name] = operator text. */
    BINARY,
    /** `c ? a : b`. [AstRole.CONDITION], [AstRole.LEFT] = then, [AstRole.RIGHT] = else. */
    TERNARY,
    /** `(int) $x` etc. [AstNode.name] = cast type; [AstRole.VALUE] = operand. */
    CAST,
    /** Single-quoted string, or double-quoted/heredoc with no embedded expressions. */
    STRING_LITERAL,
    /** Double-quoted string or heredoc with embedded expressions (those are its [AstNode.children]). */
    INTERPOLATED_STRING,
    NUMBER_LITERAL,
    /** `true` / `false` / `null` and `FOO` / `\FOO`. [AstNode.name] = constant name. */
    CONSTANT,
    /** `Foo::BAR`. [AstNode.name] = `BAR`; [AstRole.RECEIVER] = the class reference. */
    CLASS_CONSTANT,
    /** A class name used as a value or receiver (`Foo` in `Foo::bar()`). [AstNode.name] = short class name. */
    CLASS_REFERENCE,
    /** `[...]` / `array(...)`. Entries are [AstNode.arguments], each an [ARRAY_ENTRY]. */
    ARRAY_LITERAL,
    /** One entry: [AstRole.KEY] (null for list-style), [AstRole.VALUE]. */
    ARRAY_ENTRY,
    /** `echo a, b;` (also `<?= a ?>`). Echoed expressions are [AstNode.arguments]. */
    ECHO,
    /** `print a`. The operand is the single [AstNode.arguments] entry. */
    PRINT,
    /** `include`/`require`(`_once`). [AstNode.name] = keyword; path is the single argument. */
    INCLUDE,
    RETURN,
    IF,
    /** `{ ... }` statement group / function body. */
    BLOCK,
    /** Function or method parameter. [AstNode.name] = parameter name without `$`. */
    PARAMETER,
    /** Anything the adapter does not map. Children are still exposed. */
    OTHER,
    ;

    val isCall: Boolean get() = this == CALL || this == METHOD_CALL || this == STATIC_CALL || this == NEW
    val isFunctionLike: Boolean get() = this == FUNCTION || this == METHOD || this == CLOSURE
}

enum class AstRole {
    RECEIVER, KEY, VALUE, TARGET, LEFT, RIGHT, CONDITION, BODY,
}

/**
 * @param path   project-relative (or adapter-defined) file path, `/`-separated.
 * @param line   1-based line of [startOffset].
 * @param startOffset / [endOffset] 0-based character offsets into the file, end exclusive.
 */
data class SourceLocation(
    val path: String,
    val line: Int,
    val startOffset: Int,
    val endOffset: Int,
)
