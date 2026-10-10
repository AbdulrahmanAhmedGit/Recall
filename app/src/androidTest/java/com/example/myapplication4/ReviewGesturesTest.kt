package com.example.myapplication4

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.myapplication4.ui.reviewGestures
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReviewGesturesTest {
    @get:Rule val compose = createComposeRule()
    @Test fun directionsMirrorInArabicAndVerticalOrShortDragsDoNotSkip() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var enabled by mutableStateOf(true)
        var next = 0
        var previous = 0
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Box(Modifier.size(300.dp).testTag("card").reviewGestures(1, enabled, { next++ }, { previous++ }))
        } }
        val card = compose.onNodeWithTag("card")
        card.performTouchInput { swipeRight() }
        card.performTouchInput { swipeLeft() }
        assertEquals(1, next); assertEquals(1, previous)
        card.performTouchInput { swipeUp() }
        card.performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(20f, 0f)) }
        assertEquals(1, next); assertEquals(1, previous)
        compose.runOnIdle { direction = LayoutDirection.Rtl }
        card.performTouchInput { swipeLeft() }
        assertEquals(2, next); assertEquals(1, previous)
        card.performTouchInput { swipeRight() }
        assertEquals(2, next); assertEquals(2, previous)
        compose.runOnIdle { enabled = false }
        card.performTouchInput { swipeLeft() }
        assertEquals(2, next)
    }
}
