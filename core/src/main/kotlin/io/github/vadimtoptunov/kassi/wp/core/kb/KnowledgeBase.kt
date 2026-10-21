package io.github.vadimtoptunov.kassi.wp.core.kb

import io.github.vadimtoptunov.kassi.wp.core.model.Rule
import java.io.File

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
        /** Loads every `*.yml` under [dir] (e.g. a kassi-wp-rules checkout's `rules/` folder). */
        fun loadDirectory(dir: File): KnowledgeBase {
            require(dir.isDirectory) { "not a directory: $dir" }
            return load(
                dir.walkTopDown().filter { it.isFile && it.extension == "yml" }
                    .map { RuleSource(it.relativeTo(dir).path, it.readText()) }.toList(),
            )
        }

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
