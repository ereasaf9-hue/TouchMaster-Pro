package com.touchmaster.pro

import com.touchmaster.pro.engine.*
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun parserReportsLine() { val steps = MacroParser.parse("# x\nLOOP 2\nCLICK @1\nEND_LOOP"); assertEquals(3, steps.size); assertEquals(3, steps[1].line) }
    @Test fun invalidCommandFails() { try { MacroParser.parse("NOPE") ; fail() } catch (e: MacroParseException) { assertTrue(e.message!!.contains("Satır 1")) } }
    @Test fun scheduleDoesNotDrift() { assertEquals(1200L, nextDeadline(1000L, 2, 100L)) }
}
