package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JL005UnpreparedSqlInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/JL-005"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(JL005UnpreparedSqlInspection())
    }

    private fun highlight(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    // Covers both receiver-agnostic getters (getString) and the receiver-qualified
    // $input->get / $app->input->get, plus a superglobal.
    fun testPositive() = highlight("positive.php")

    // Casts, $db->quote(), a constant query, and $params->get() (not request input) stay silent.
    fun testNegative() = highlight("negative.php")
}
