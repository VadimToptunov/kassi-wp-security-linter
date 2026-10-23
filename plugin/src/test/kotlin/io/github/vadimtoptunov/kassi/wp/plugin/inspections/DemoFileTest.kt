package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * docs/demo/demo-plugin.php is the Marketplace screenshot file: every rule must fire in it at least
 * once as a full (non-weak) warning or error, so the screenshots show every inspection.
 */
class DemoFileTest : BasePlatformTestCase() {
    fun testEveryRuleFiresInTheDemoFile() {
        val demo = File("../docs/demo/demo-plugin.php").readText()
        val missing = Inspections.byRuleId.filter { (_, tool) ->
            runInspection(project, tool, "demo-plugin.php", demo).none { !it.message.contains("Weak:") && !it.message.contains("Low impact") && !it.message.contains("Unless the handler") }
        }.keys
        assertTrue("rules with no full finding in the demo file: $missing", missing.isEmpty())
    }
}
