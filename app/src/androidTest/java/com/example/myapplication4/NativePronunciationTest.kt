package com.example.myapplication4

import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.pronunciation.PronunciationManager
import com.example.myapplication4.pronunciation.SpeechStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class NativePronunciationTest {
    @Test fun nativeEngineHandlesEnglishGermanMissingVoiceAndLifecycle() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var manager: PronunciationManager
        instrumentation.runOnMainSync { manager = PronunciationManager(instrumentation.targetContext) }
        try {
            for ((word, language) in listOf("instructions" to "en", "Guten Morgen" to "de")) {
                instrumentation.runOnMainSync { manager.speak(word, language, 1f) }
                val result = withTimeout(15_000) {
                    manager.status.first { it == SpeechStatus.Idle || it == SpeechStatus.Unavailable }
                }
                println("Native pronunciation " + language + ": " + result + "; " + manager.message.value)
                if (result == SpeechStatus.Unavailable) {
                    assertNotNull(manager.message.value)
                    assertTrue(manager.message.value!!.contains("voice") || manager.message.value!!.contains("speech"))
                }
            }
            // This well-formed but unavailable language must never fall back to English.
            instrumentation.runOnMainSync { manager.speak("test", "zz-ZZ", 1f) }
            withTimeout(15_000) { manager.status.first { it == SpeechStatus.Unavailable } }
            assertNotNull(manager.message.value)
            instrumentation.runOnMainSync {
                manager.speak("directions", "en", .8f)
                manager.speak("instructions", "en", 1.2f)
                manager.stop()
            }
            assertEquals(SpeechStatus.Idle, manager.status.value)
            instrumentation.runOnMainSync { manager.close(); manager.speak("ignored after close", "en", 1f) }
            assertEquals(SpeechStatus.Idle, manager.status.value)
        } finally { instrumentation.runOnMainSync { manager.close() } }
    }
}
