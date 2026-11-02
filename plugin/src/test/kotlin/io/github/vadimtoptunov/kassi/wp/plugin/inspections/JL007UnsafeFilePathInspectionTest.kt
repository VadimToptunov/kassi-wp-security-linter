package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JL007UnsafeFilePathInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/JL-007"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(JL007UnsafeFilePathInspection())
    }

    private fun highlight(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    // getString/getPath and a raw superglobal into require_once / readfile / file_get_contents.
    fun testPositive() = highlight("positive.php")

    // basename(), the cmd-filtered getCmd()/get(), JPath::clean() (static) and getInt() stay silent.
    fun testNegative() = highlight("negative.php")
}
