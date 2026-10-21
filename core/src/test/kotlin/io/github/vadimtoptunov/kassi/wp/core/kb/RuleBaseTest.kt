package io.github.vadimtoptunov.kassi.wp.core.kb

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates the real rule base (kassi-wp-rules submodule), not a fixture. */
class RuleBaseTest {
    private val rulesDir = File(requireNotNull(System.getProperty("kassi.rules.dir")) { "kassi.rules.dir not set" })

    private val files = rulesDir.walkTopDown().filter { it.isFile && it.extension == "yml" }.toList()

    @Test
    fun `every rule file in kassi-wp-rules is valid and the base loads`() {
        assertTrue(files.isNotEmpty(), "no rules under $rulesDir. Run: git submodule update --init")
        val kb = KnowledgeBase.load(files.map { RuleSource(it.relativeTo(rulesDir).path, it.readText()) })
        assertEquals(files.size, kb.rules.size)
    }

    @Test
    fun `file name matches rule id and every reference points to a corpus case`() {
        for (f in files) {
            val rule = RuleParser.parse(f.readText(), f.name)
            assertEquals("${rule.id}.yml", f.name)
            assertTrue(rule.references.all { it.corpus?.startsWith("${rule.id}/") == true }, "${rule.id}: every reference needs a corpus case of this rule")
        }
    }
}
