package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WP018HttpHeaderInjectionInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/WP-018"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(WP018HttpHeaderInjectionInspection())
    }

    private fun highlight(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testPositive() = highlight("positive.php")

    fun testSanitizer() = highlight("sanitizer.php")

    fun testNegative() = highlight("negative.php")
}
