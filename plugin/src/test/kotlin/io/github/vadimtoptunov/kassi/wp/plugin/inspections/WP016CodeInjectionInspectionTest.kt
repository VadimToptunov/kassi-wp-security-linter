package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WP016CodeInjectionInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/WP-016"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(WP016CodeInjectionInspection())
    }

    private fun highlight(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testPositive() = highlight("positive.php")

    fun testSanitizer() = highlight("sanitizer.php")

    fun testNegative() = highlight("negative.php")
}
