package com.example.myapplication4.domain

import java.io.PushbackReader
import java.io.Reader
import org.json.JSONArray
import org.json.JSONObject

/** Streams the large history array; JSON values are still validated by the existing codec.
 * Not a new backup contract. Accepts both compact and older indented v1–v3 documents.
 */
internal class BackupEnvelopeReader(reader: Reader) {
    private val input = PushbackReader(reader.buffered(), 1)
    private fun next(): Int { var c = input.read(); while (c >= 0 && c.toChar().isWhitespace()) c = input.read(); return c }
    private fun expect(c: Char) { require(next() == c.code) { "Invalid JSON delimiter" } }
    private fun value(limit: Int): String {
        val out = StringBuilder()
        var depth = 0
        var quoted = false
        var escaped = false
        var c = next()
        require(c >= 0)
        while (c >= 0) {
            val char = c.toChar()
            if (!quoted && depth == 0 && (char == ',' || char == '}' || char == ']')) { input.unread(c); break }
            out.append(char); require(out.length <= limit)
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 64) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
            c = input.read()
        }
        require(!quoted && depth == 0 && out.isNotBlank())
        return out.toString().trim()
    }
    fun read(log: (JSONObject) -> Unit): String {
        val fields = HashSet<String>()
        val metadata = StringBuilder("{")
        expect('{')
        var c = next()
        if (c == '}'.code) { require(next() == -1); return "{}" }
        input.unread(c)
        while (true) {
            // Read a JSON string key separately: colon is not a value delimiter.
            expect('"')
            val key = StringBuilder("\"")
            var escaped = false
            while (true) {
                c = input.read(); require(c >= 0 && key.length < 1024)
                key.append(c.toChar())
                if (!escaped && c == '"'.code) break
                escaped = !escaped && c == '\\'.code
            }
            val name = JSONArray("[${key}]").getString(0)
            require(fields.add(name))
            expect(':')
            if (name == "review_logs") {
                expect('[')
                c = next()
                if (c != ']'.code) {
                    input.unread(c)
                    while (true) {
                        log(JSONObject(value(1_000_000)))
                        c = next()
                        if (c == ']'.code) break
                        require(c == ','.code)
                    }
                }
            } else {
                if (metadata.length > 1) metadata.append(',')
                metadata.append(key).append(':').append(value(50_000_000))
                require(metadata.length <= 50_000_000)
            }
            c = next()
            if (c == '}'.code) break
            require(c == ','.code)
        }
        require(next() == -1) { "Trailing backup content" }
        return metadata.append('}').toString()
    }
}
