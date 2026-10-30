package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WP002MissingNonceVerificationInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/WP-002"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(WP002MissingNonceVerificationInspection())
    }

    private fun highlight(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testPositive() = highlight("positive.php")

    fun testSanitizer() = highlight("sanitizer.php")

    fun testNegative() = highlight("negative.php")

    /**
     * Cross-file gate helper: a handler that calls `Helpers::verify_ajax_call()` (defined in another
     * file, where it runs check_ajax_referer) is NOT reported; a handler whose cross-file helper does
     * not verify a nonce still is. Proves resolution is real, not "any cross-file call suppresses".
     */
    fun testCrossFileGateHelper() {
        myFixture.configureByFiles("crossfile.php", "crossfile-helpers.php")
        myFixture.checkHighlighting(true, false, true)
    }
}
