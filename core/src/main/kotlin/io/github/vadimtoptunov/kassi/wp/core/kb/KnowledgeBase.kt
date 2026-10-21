package io.github.vadimtoptunov.kassi.wp.core.kb

import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/** One rule file: where it came from (for error messages) and its YAML text. */
data class RuleSource(val origin: String, val yaml: String)

class KnowledgeBaseException(val errors: List<String>) :
    RuntimeException("Rule base is invalid:\n" + errors.joinToString("\n"))

/**
 * The loaded, validated rule base. Construction is all-or-nothing: any invalid file or duplicate id
 * fails the whole load. A half-loaded rule base would be non-deterministic from the user's point of view.
 */
class KnowledgeBase private constructor(private val byId: Map<String, Rule>) {

    val rules: Collection<Rule> get() = byId.values

    fun rule(id: String): Rule? = byId[id]

    fun require(id: String): Rule = byId[id] ?: error("rule $id is not in the rule base")

    companion object {
        fun load(sources: List<RuleSource>): KnowledgeBase {
            val errors = mutableListOf<String>()
            val byId = linkedMapOf<String, Rule>()
            val originOf = mutableMapOf<String, String>()
            for (src in sources.sortedBy { it.origin }) {
                val rule = try {
                    RuleParser.parse(src.yaml, src.origin)
                } catch (e: RuleValidationException) {
                    errors += e.errors.map { "${e.origin}: $it" }
                    continue
                }
                val previous = originOf.put(rule.id, src.origin)
                if (previous != null) {
                    errors += "${src.origin}: duplicate rule id ${rule.id} (also in $previous)"
                } else {
                    byId[rule.id] = rule
                }
            }
            if (errors.isNotEmpty()) throw KnowledgeBaseException(errors)
            return KnowledgeBase(byId)
        }
    }
}
