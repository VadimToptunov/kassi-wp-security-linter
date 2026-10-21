package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WP008UnsafeRedirectInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/WP-008"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(WP008UnsafeRedirectInspection())
    }

    private fun highlight(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testPositive() = highlight("positive.php")

    fun testSanitizer() = highlight("sanitizer.php")

    fun testNegative() = highlight("negative.php")

    fun testQuickFixSwitchesToSafeRedirect() {
        myFixture.configureByFile("quickfix.php")
        myFixture.launchAction(myFixture.findSingleIntention("Use wp_safe_redirect()"))
        myFixture.checkResultByFile("quickfix_after.php")
    }
}
