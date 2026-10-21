package io.github.vadimtoptunov.kassi.wp.plugin.rules

import io.github.vadimtoptunov.kassi.wp.core.kb.KnowledgeBase
import io.github.vadimtoptunov.kassi.wp.core.kb.RuleSource

/**
 * The rule base shipped inside the plugin (copied from kassi-wp-rules at build time into
 * `kassi/wp-rules/`). Loaded once, from the plugin's own resources only. No file system or network
 * access. A load failure is a build defect (RuleBaseTest / BuiltInRulesTest guard it), so it throws.
 */
object BuiltInRules {
    private const val DIR = "kassi/wp-rules"

    val knowledgeBase: KnowledgeBase by lazy { load() }

    private fun load(): KnowledgeBase {
        val loader = BuiltInRules::class.java.classLoader
        val index = loader.getResource("$DIR/index.txt")?.readText()
            ?: error("$DIR/index.txt missing from plugin resources")
        val sources = index.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { name ->
            val yaml = loader.getResource("$DIR/$name")?.readText() ?: error("$DIR/$name listed in index but missing")
            RuleSource("$DIR/$name", yaml)
        }
        return KnowledgeBase.load(sources)
    }
}
