package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JL006UnescapedOutputInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/JL-006"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(JL006UnescapedOutputInspection())
    }

    private fun highlight(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    // getRaw / getHtml and a raw superglobal, echoed or printed, plus the chained $app->input->getRaw.
    fun testPositive() = highlight("positive.php")

    // htmlspecialchars, $view->escape(), the cmd-filtered get()/getCmd() and getInt() stay silent.
    fun testNegative() = highlight("negative.php")
}
