package io.github.vadimtoptunov.kassi.wp.plugin.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.vadimtoptunov.kassi.wp.plugin.inspections.WP005UnpreparedSqlInspection
import io.github.vadimtoptunov.kassi.wp.plugin.report.GenerateReportAction
import io.github.vadimtoptunov.kassi.wp.plugin.rules.ProjectRuleBase
import java.io.File
import java.nio.file.Files

class SettingsAndReportTest : BasePlatformTestCase() {
    private val vulnerable = "<?php\nfunction f() { global \$wpdb; \$wpdb->query(\"DELETE FROM t WHERE id = \" . \$_GET['id']); }\n"
    private val sanitizedBySomething = "<?php\nfunction g() { global \$wpdb; \$wpdb->query(\"DELETE FROM t WHERE id = \" . my_clean(\$_GET['id'])); }\n"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(WP005UnpreparedSqlInspection())
    }

    override fun tearDown() {
        try {
            KassiSettings.getInstance(project).loadState(KassiSettings.State())
            ProjectRuleBase.getInstance(project).reset()
        } finally {
            super.tearDown()
        }
    }

    private fun findingsIn(path: String, code: String): Int {
        val f = myFixture.addFileToProject(path, code)
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        return myFixture.doHighlighting().count { it.inspectionToolId == "WP005UnpreparedSql" }
    }

    fun testExcludedPathsAreSkipped() {
        assertEquals(1, findingsIn("src/a.php", vulnerable))
        assertEquals(0, findingsIn("vendor/lib/a.php", vulnerable))
        // Replacing the list drops the vendor/ default.
        KassiSettings.getInstance(project).state.excludedPaths = "legacy/"
        assertEquals(0, findingsIn("legacy/b.php", vulnerable))
        assertEquals(1, findingsIn("vendor/lib/b.php", vulnerable))
    }

    fun testRulePackOverridesBuiltInRuleById() {
        // Unknown functions end the proof, so my_clean() is silent; a pack that lists it as passthrough is not.
        assertEquals(0, findingsIn("src/c.php", sanitizedBySomething))
        val builtIn = javaClass.classLoader.getResource("kassi/wp-rules/WP-005.yml")!!.readText()
        val packDir = Files.createTempDirectory("kassi-pack").toFile()
        File(packDir, "WP-005.yml").writeText(builtIn.replace("  passthrough: [wp_unslash,", "  passthrough: [my_clean, wp_unslash,"))
        KassiSettings.getInstance(project).state.rulePackDir = packDir.absolutePath
        ProjectRuleBase.getInstance(project).reset()
        assertEquals(1, findingsIn("src/d.php", sanitizedBySomething))
    }

    fun testInvalidRulePackFallsBackToBuiltInRules() {
        val packDir = Files.createTempDirectory("kassi-bad-pack").toFile()
        File(packDir, "WP-005.yml").writeText("id: WP-005\n")
        KassiSettings.getInstance(project).state.rulePackDir = packDir.absolutePath
        ProjectRuleBase.getInstance(project).reset()
        assertEquals(1, findingsIn("src/e.php", vulnerable))
    }

    fun testReportCollectsFindingsWithRuleMetadata() {
        myFixture.addFileToProject("src/r.php", vulnerable)
        myFixture.addFileToProject("vendor/r.php", vulnerable)
        val report = GenerateReportAction.buildReport(project, null)
        val wp005 = report.entries.filter { it.rule.id == "WP-005" }
        assertEquals(1, wp005.size)
        assertTrue(wp005.single().path.endsWith("src/r.php"))
        assertEquals(2, wp005.single().line)
        assertTrue(report.markdown().contains("WP-005"))
        assertTrue(report.json().contains("\"cwe\": 89"))
    }

    fun testReportScansTheDemoFileAndFindsEveryRule() {
        val demo = java.io.File("../docs/demo/demo-plugin.php").readText()
        myFixture.addFileToProject("demo/demo-plugin.php", demo)
        val report = GenerateReportAction.buildReport(project, null)
        assertEquals(1, report.filesScanned)
        val rulesFound = report.entries.map { it.rule.id }.toSet()
        // WP-020's real engine is plugin-wide (bounty-pipeline's StoredXssCheck): writer and
        // reader almost always live in different files, so the per-file IDE inspection is a
        // no-op and never fires on a single demo file. Pipeline tests cover its recall.
        val expected = io.github.vadimtoptunov.kassi.wp.plugin.inspections.Inspections.byRuleId.keys - "WP-020"
        assertEquals(expected, rulesFound)
        assertEquals(23, report.rulesChecked.size)
    }

    fun testEmptyProjectStillListsTheRulesThatWouldBeChecked() {
        val report = GenerateReportAction.buildReport(project, null)
        assertEquals(0, report.filesScanned)
        assertEquals(23, report.rulesChecked.size)
    }
}
