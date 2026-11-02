package io.github.vadimtoptunov.kassi.wp.core.flow

import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/** A proven flow from untrusted input. [sources] are display labels such as `$_GET` or `->get_param()`. */
data class Taint(val sources: Set<String>) {
    operator fun plus(other: Taint?): Taint = if (other == null) this else Taint(sources + other.sources)

    val label: String get() = sources.sorted().joinToString(", ")

    companion object {
        fun union(items: Iterable<Taint?>): Taint? = items.filterNotNull().reduceOrNull { a, b -> a + b }
    }
}

/**
 * A taint source, written in the rule's `source:` list as one of:
 *  - `$_GET`                a superglobal, any key
 *  - `$_SERVER[REQUEST_URI]` a superglobal, only that key; `HTTP_*` matches by prefix
 *  - `->get_param`          a method of any receiver (WP_REST_Request accessors)
 *  - `$input->get`          a method of a receiver named `input` — the variable `$input`/`$jinput`
 *                           OR a property `->input` (`$app->input->get`, `$this->input->get`). Lets a
 *                           high-collision method name (Joomla `->get`, shared with Registry/params)
 *                           be a source only on the request-input object.
 *  - `name`                 a function whose return value is input
 */
sealed interface SourceSpec {
    data class Superglobal(val name: String, val keyPattern: String?) : SourceSpec {
        fun matchesKey(key: String?): Boolean = when {
            keyPattern == null -> true
            key == null -> false
            keyPattern.endsWith("*") -> key.startsWith(keyPattern.dropLast(1))
            else -> key == keyPattern
        }
    }

    data class Method(val name: String) : SourceSpec

    /** Method [name] called on a receiver whose variable or property name is [receiver] (both lowercased). */
    data class QualifiedMethod(val receiver: String, val name: String) : SourceSpec

    data class Function(val name: String) : SourceSpec

    companion object {
        private val SUPERGLOBAL = Regex("""^\$(_[A-Z]+)(?:\[([A-Za-z0-9_*]+)])?$""")
        private val QUALIFIED = Regex("""^\$([A-Za-z_][A-Za-z0-9_]*)->([A-Za-z_][A-Za-z0-9_]*)$""")
        private val IDENT = Regex("""^[A-Za-z_][A-Za-z0-9_]*$""")

        fun parse(s: String): SourceSpec {
            SUPERGLOBAL.matchEntire(s)?.let { m -> return Superglobal(m.groupValues[1], m.groupValues[2].ifEmpty { null }) }
            QUALIFIED.matchEntire(s)?.let { m -> return QualifiedMethod(m.groupValues[1].lowercase(), m.groupValues[2].lowercase()) }
            if (s.startsWith("->") && IDENT.matches(s.drop(2))) return Method(s.drop(2).lowercase())
            require(IDENT.matches(s)) { "invalid source '$s' (expected \$_NAME, \$_NAME[KEY], \$recv->method, ->method or function)" }
            return Function(s.lowercase())
        }
    }
}

/**
 * Everything the taint engine knows about one rule, all of it from YAML:
 *  - `source:`                [sources]
 *  - `sanitizers:`            function names, or `->method` for methods (e.g. `->prepare`)
 *  - `params.passthrough`     functions whose result carries the taint of their arguments
 *  - `params.format_functions` sprintf-like: taint flows only through `%s` placeholders
 *  - `params.guard_functions` allow-list/format checks on their first argument (`in_array($x, $allowed)`,
 *    `is_numeric($x)`): the argument is clean inside `if (guard)` and after `if (!guard) { return; }`
 *  - `params.terminators`      calls that end the request (`wp_die`, `wp_send_json_error`, ...)
 *  - `params.clean_keys`       keys of `$_FILES` entries whose values are not attacker-controlled
 *                              (`$_FILES['f']['tmp_name']` is a server-generated path)
 *  - `params.host_prefix_cleans` `[true]` for URL rules: input after a fixed `scheme://host/` is clean
 *  - `params.filter_arg_readers` reader methods whose 3rd argument is a type-filter string
 *    (Joomla `$input->get($name, $default, 'INT')`); paired with [sanitizingFilters]
 *  - `params.sanitizing_filters` the filter literals (`int`, `uint`, `float`, ...) that cast the read
 *    value to a safe type — a [filterArgReaders] call with such a 3rd arg is treated as sanitized
 */
class TaintSpec(
    val sources: List<SourceSpec>,
    val sanitizerFunctions: Set<String>,
    val sanitizerMethods: Set<String>,
    val passthrough: Set<String>,
    val formatFunctions: Set<String>,
    val guardFunctions: Set<String> = emptySet(),
    val terminators: Set<String> = emptySet(),
    val cleanKeys: Set<String> = emptySet(),
    val hostPrefixCleans: Boolean = false,
    val filterArgReaders: Set<String> = emptySet(),
    val sanitizingFilters: Set<String> = emptySet(),
) {
    companion object {
        fun of(rule: Rule): TaintSpec {
            val (methods, functions) = rule.sanitizers.partition { it.startsWith("->") }
            return TaintSpec(
                sources = rule.source.map(SourceSpec::parse),
                sanitizerFunctions = functions.map { it.lowercase() }.toSet(),
                sanitizerMethods = methods.map { it.drop(2).lowercase() }.toSet(),
                passthrough = rule.params.strings("passthrough").map { it.lowercase() }.toSet(),
                formatFunctions = rule.params.strings("format_functions").map { it.lowercase() }.toSet(),
                guardFunctions = rule.params.strings("guard_functions").map { it.lowercase() }.toSet(),
                terminators = rule.params.strings("terminators").map { it.lowercase() }.toSet(),
                cleanKeys = rule.params.strings("clean_keys").toSet(),
                hostPrefixCleans = rule.params.strings("host_prefix_cleans").firstOrNull()?.toBoolean() == true,
                filterArgReaders = rule.params.strings("filter_arg_readers").map { it.lowercase() }.toSet(),
                sanitizingFilters = rule.params.strings("sanitizing_filters").map { it.lowercase() }.toSet(),
            )
        }
    }
}
