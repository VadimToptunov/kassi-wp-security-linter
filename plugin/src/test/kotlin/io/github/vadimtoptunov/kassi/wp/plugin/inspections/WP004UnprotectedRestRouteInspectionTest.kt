package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WP004UnprotectedRestRouteInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/WP-004"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(WP004UnprotectedRestRouteInspection())
    }

    private fun highlight(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testPositive() = highlight("positive.php")

    fun testNegative() = highlight("negative.php")

    fun testSanitizer() = highlight("sanitizer.php")

    fun testGetException() = highlight("get_exception.php")

    fun testQuickFixMakesPublicReadOnlyRouteExplicit() {
        myFixture.configureByFile("quickfix.php")
        // Only the route under the caret is changed; the second one keeps its own finding.
        myFixture.launchAction(myFixture.findSingleIntention(FIX))
        myFixture.checkResultByFile("quickfix_after.php")
    }

    fun testQuickFixOnSingleLineArray() {
        myFixture.configureByText("t.php", "<?php\nregister_rest_<caret>route('p/v1', '/y', ['methods' => 'GET', 'callback' => 'p_y']);\n")
        myFixture.launchAction(myFixture.findSingleIntention(FIX))
        myFixture.checkResult("<?php\nregister_rest_route('p/v1', '/y', ['methods' => 'GET', 'callback' => 'p_y', 'permission_callback' => '__return_true']);\n")
    }

    fun testNoQuickFixOnWriteRoutes() {
        myFixture.configureByText("t.php", "<?php\nregister_rest_<caret>route('p/v1', '/z', ['methods' => 'POST', 'callback' => 'p_z']);\n")
        assertEquals(1, myFixture.doHighlighting().count { it.inspectionToolId == "WP004UnprotectedRestRoute" })
        assertTrue(myFixture.getAllQuickFixes().none { it.familyName == FIX })
    }

    private companion object {
        const val FIX = "Declare 'permission_callback' => '__return_true' (route stays public)"
    }
}
