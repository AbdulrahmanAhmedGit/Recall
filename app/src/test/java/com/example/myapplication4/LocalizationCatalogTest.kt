package com.example.myapplication4

import com.example.myapplication4.data.UserSettings
import com.example.myapplication4.domain.BackupResult
import com.example.myapplication4.domain.RecallBackupCodec
import com.example.myapplication4.util.RecallLocale
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationCatalogTest {
    private fun resources(language: String): Map<String, Element> {
        val base = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val folder = File(base, if (language == "en") "values" else "values-$language")
        val entries = folder.listFiles().orEmpty().filter { it.extension == "xml" }.flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
            (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                .filter { it.tagName in setOf("string", "string-array", "plurals") && it.getAttribute("translatable") != "false" }
        }
        assertEquals("Duplicate resources in $language", entries.size, entries.map { it.tagName + "/" + it.getAttribute("name") }.toSet().size)
        return entries.associateBy { it.tagName + "/" + it.getAttribute("name") }
    }

    private fun formats(text: String) = Regex("%\\d+\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test fun allFiveLanguagesHaveCompleteResourceAndFormatCoverage() {
        val english = resources("en")
        assertTrue(english.size > 300)
        for (language in RecallLocale.languages.drop(1)) {
            val translated = resources(language)
            assertEquals("Missing translations for $language", english.keys, translated.keys)
            english.forEach { (key, original) ->
                val actual = translated.getValue(key)
                assertEquals("Resource type changed: $language/$key", original.tagName, actual.tagName)
                when (original.tagName) {
                    "string" -> {
                        assertTrue("Empty translation: $language/$key", actual.textContent.isNotBlank())
                        assertEquals("Format mismatch: $language/$key", formats(original.textContent), formats(actual.textContent))
                    }
                    "string-array" -> assertEquals(original.getElementsByTagName("item").length, actual.getElementsByTagName("item").length)
                    "plurals" -> {
                        val items = actual.getElementsByTagName("item")
                        val forms = (0 until items.length).map { items.item(it) as Element }
                        assertTrue(forms.any { it.getAttribute("quantity") == "other" })
                        val expected = formats(original.getElementsByTagName("item").item(0).textContent)
                        forms.forEach { form ->
                            // Arabic one/two forms may spell out the quantity without a number.
                            if (formats(form.textContent).isNotEmpty()) assertEquals("Plural format: $language/$key", expected, formats(form.textContent))
                        }
                        if (language == "ar") assertEquals(setOf("zero", "one", "two", "few", "many", "other"), forms.map { it.getAttribute("quantity") }.toSet())
                    }
                }
            }
        }
    }

    @Test fun explicitAndSystemLocalesKeepRegionalDateRulesAndFallbackSafely() {
        assertEquals(Locale.forLanguageTag("ar-EG"), RecallLocale.resolve("ar", Locale.forLanguageTag("en-EG")))
        assertEquals(Locale.forLanguageTag("fr-CA"), RecallLocale.resolve("system", Locale.forLanguageTag("fr-CA")))
        assertEquals(Locale.forLanguageTag("en-JP"), RecallLocale.resolve("system", Locale.JAPAN))
        assertEquals(Locale.forLanguageTag("de-US"), RecallLocale.resolve("de", Locale.US))
    }

    @Test fun backupsRoundTripEverySupportedUiLanguageWithoutChangingStudyData() {
        val raw = """{"format":"recall-backup","version":3,"subjects":[],"chapters":[],"lessons":[],"cards":[],"tags":[],"lesson_tags":[],"review_states":[],"review_logs":[],"schedules":[],"resources":[],"pronunciation_targets":[],"preferences":{}}"""
        val empty = (RecallBackupCodec.decode(raw) as BackupResult.Success).data
        for (language in RecallLocale.languages) {
            val encoded = RecallBackupCodec.encode(empty, UserSettings(language = language))
            val decoded = RecallBackupCodec.decode(encoded) as BackupResult.Success
            assertEquals(language, decoded.settings.language)
            assertEquals(empty, decoded.data)
        }
    }
}
