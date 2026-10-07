package com.example.myapplication4.util

import java.io.Reader

/** Bounds allocation before accepting external text; caller owns/ closes the reader. */
internal fun readBoundedText(reader: Reader, maxChars: Int): String {
    require(maxChars >= 0)
    val result = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) return result.toString()
        require(count <= maxChars - result.length) { "That file is too large. Recall accepts JSON files up to 2 MB." }
        result.append(buffer, 0, count)
    }
}
