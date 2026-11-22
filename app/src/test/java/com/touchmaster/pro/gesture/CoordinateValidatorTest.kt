package com.touchmaster.pro.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateValidatorTest {
    @Test fun acceptsInsideAndRejectsOutside() {
        assertTrue(CoordinateValidator.validate(10f, 20f, 100, 200).isSuccess)
        assertFalse(CoordinateValidator.validate(100f, 20f, 100, 200).isSuccess)
        assertFalse(CoordinateValidator.validate(-1f, 20f, 100, 200).isSuccess)
    }
    @Test fun normalizationRoundTrips() {
        val normalized = CoordinateValidator.normalize(250f, 500f, 1000, 2000)
        val restored = CoordinateValidator.denormalize(normalized, 1000, 2000)
        assertEquals(250f, restored.x, 0.0001f)
        assertEquals(500f, restored.y, 0.0001f)
    }
}
