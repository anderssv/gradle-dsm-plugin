package dsm

import kotlin.test.Test
import kotlin.test.assertTrue

class DsmAgentHelpTextTest {

    @Test
    fun `agent help text contains task name`() {
        val text = DsmAgentHelpText.generate()

        assertTrue(text.contains("dsm"), "Should mention the dsm task")
    }

    @Test
    fun `agent help text includes workflow guidance`() {
        val text = DsmAgentHelpText.generate()

        assertTrue(text.contains("BASELINE"))
        assertTrue(text.contains("WORK"))
        assertTrue(text.contains("VERIFY"))
    }

    @Test
    fun `agent help text explains how to read the matrix`() {
        val text = DsmAgentHelpText.generate()

        assertTrue(text.contains("row depends on column"))
        assertTrue(text.contains("below the diagonal"))
        assertTrue(text.contains("above the diagonal"))
    }

    @Test
    fun `agent help text includes parameter documentation`() {
        val text = DsmAgentHelpText.generate()

        assertTrue(text.contains("rootPackage"))
        assertTrue(text.contains("depth"))
        assertTrue(text.contains("-Pdsm.depth"))
        assertTrue(text.contains("-Pdsm.html"))
    }

    @Test
    fun `agent help text includes tips`() {
        val text = DsmAgentHelpText.generate()

        assertTrue(text.contains("Tips for Optimal Results"))
        assertTrue(text.contains("cyclic"))
    }

    @Test
    fun `agent help text explains cyclic dependency output`() {
        val text = DsmAgentHelpText.generate()

        assertTrue(text.contains("Cyclic dependencies detected"))
        assertTrue(text.contains("class-level edges"))
    }
}
