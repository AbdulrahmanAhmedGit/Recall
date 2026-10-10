package com.example.myapplication4

import com.example.myapplication4.ui.design.interpolateRecallNumber
import org.junit.Assert.*
import org.junit.Test

class MotionNumberTest {
    @Test fun endpointsAreExactIncludingLargeIntegersAndDecimals() {
        val target = 1_234_567_890.125
        assertEquals(target, interpolateRecallNumber(0.0, target, 1f), 0.0)
        assertEquals(42.25, interpolateRecallNumber(42.25, target, 0f), 0.0)
        assertEquals(.9375, interpolateRecallNumber(.8125, .9375, 1f), 0.0)
    }
    @Test fun upwardAndDownwardUpdatesStayBetweenEndpoints() {
        for (step in 0..100) {
            val fraction = step / 100f
            assertTrue(interpolateRecallNumber(250.0, 1250.0, fraction) in 250.0..1250.0)
            assertTrue(interpolateRecallNumber(1250.0, 250.0, fraction) in 250.0..1250.0)
        }
        assertEquals(750.0, interpolateRecallNumber(250.0, 1250.0, .5f), 0.0)
    }
    @Test fun interruptedAnimationContinuesFromItsDisplayedValue() {
        val displayed = interpolateRecallNumber(0.0, 1250.0, .4f)
        assertEquals(displayed, interpolateRecallNumber(displayed, 300.0, 0f), 0.0)
        assertEquals(300.0, interpolateRecallNumber(displayed, 300.0, 1f), 0.0)
    }
}
