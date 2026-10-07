package com.example.myapplication4

import com.example.myapplication4.util.readBoundedText
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class TextFilesTest {
    @Test fun preservesMixedTextAndAcceptsExactLimit() {
        val text = "العناصر الانتقالية [[chem:Fe₂O₃(s) + 3CO(g) → 2Fe(s) + 3CO₂(g)]]\nKotlin"
        assertEquals(text, readBoundedText(StringReader(text), text.length))
        assertEquals("", readBoundedText(StringReader(""), 0))
    }
    @Test fun rejectsOversizedFileBeforeReadingTheEntireStream() {
        var consumed = 0
        val source = object : java.io.Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                consumed += length
                buffer.fill('x', offset, offset + length)
                return length
            }
            override fun close() = Unit
        }
        try { readBoundedText(source, 9000); fail("Expected size rejection") }
        catch (_: IllegalArgumentException) { assertTrue(consumed <= 16384) }
    }
}
