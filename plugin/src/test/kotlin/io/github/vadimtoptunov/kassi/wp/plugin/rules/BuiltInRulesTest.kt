package io.github.vadimtoptunov.kassi.wp.plugin.rules

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.vadimtoptunov.kassi.wp.plugin.inspections.Inspections

/** The rule base packaged into the plugin loads and matches what plugin.xml registers. */
class BuiltInRulesTest : BasePlatformTestCase() {

    fun testBuiltInRuleBaseLoadsFromPluginResources() {
        val rule = BuiltInRules.knowledgeBase.require("WP-004")
        assertEquals(862, rule.cwe)
    }

    fun testEveryPluginOverlayPointsToThatInspectionsDescription() {
        for (rule in BuiltInRules.knowledgeBase.rules) {
            val overlay = rule.plugin ?: continue
            val tool = Inspections.byRuleId[rule.id] ?: error("${rule.id}: no inspection")
            assertEquals("inspectionDescriptions/${tool.shortName}.html", overlay.descriptionHtml)
            assertNotNull("${overlay.descriptionHtml} missing", javaClass.classLoader.getResource(overlay.descriptionHtml))
        }
    }
}
