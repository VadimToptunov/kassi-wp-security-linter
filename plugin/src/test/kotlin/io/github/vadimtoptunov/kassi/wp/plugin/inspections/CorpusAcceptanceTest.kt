package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules
import java.io.File

/**
 * Acceptance invariant: every rule is backed by kassi-wp-corpus cases from real CVEs, and for each
 * case the inspection flags vulnerable.php (recall) and stays silent on fixed.php (precision).
 */
class CorpusAcceptanceTest : BasePlatformTestCase() {

    private val corpus = File(requireNotNull(System.getProperty("kassi.corpus.dir")) { "kassi.corpus.dir not set" })

    private fun findings(tool: LocalInspectionTool, file: File): Int {
        myFixture.configureByText("${file.parentFile.name}-${file.name}", file.readText())
        return myFixture.doHighlighting().count { it.inspectionToolId == tool.shortName }
    }

    fun testEveryRuleHasAnInspectionAndProvenCorpusCases() {
        assertTrue("corpus missing at $corpus. Run: git submodule update --init", corpus.isDirectory)
        for (rule in BuiltInRules.knowledgeBase.rules) {
            val tool = Inspections.byRuleId[rule.id]
            assertNotNull("${rule.id} has no registered inspection", tool)
            myFixture.enableInspections(tool!!)

            val referenced = rule.references.mapNotNull { it.corpus }.toSet()
            val onDisk = File(corpus, rule.id).listFiles { f -> f.isDirectory }?.map { "${rule.id}/${it.name}" }?.toSet().orEmpty()
            assertFalse("${rule.id}: no corpus case", referenced.isEmpty())
            assertEquals("${rule.id}: references and corpus cases differ", onDisk, referenced)

            for (case in referenced.sorted()) {
                val dir = File(corpus, case)
                assertTrue("$case: vulnerable.php must produce a finding", findings(tool, File(dir, "vulnerable.php")) > 0)
                assertEquals("$case: fixed.php must be silent", 0, findings(tool, File(dir, "fixed.php")))
            }
        }
    }
}
