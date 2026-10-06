package com.example.myapplication4

import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubjectFeaturesTest {
    @Test fun subjectPromptPinsExactNamesIncludingQuotesAndArabic() {
        val name = "Chemistry \"العناصر\""
        val prompt = subjectAiPrompt(name, "Oxidation states")
        assertTrue(prompt.contains(JSONObject.quote(name)))
        assertTrue(prompt.contains("Append new cards to the existing lesson"))
        assertTrue(prompt.contains("never translate"))
    }
    @Test fun normalizedSubjectNamesIgnoreCaseSpacingAndCanonicalUnicode() {
        assertEquals(normalizedName("  CHEMISTRY  "), normalizedName("Chemistry"))
        assertEquals(normalizedName("Science   notes"), normalizedName("science notes"))
        assertEquals(normalizedName("Café"), normalizedName("Cafe\u0301"))
        assertNotEquals(normalizedName("Physics"), normalizedName("Chemistry"))
    }
    @Test fun resourceBackupPreservesNotesAndAttachmentReferences() {
        val subject = SubjectEntity(id = "subject", name = "Chemistry")
        val note = SubjectResourceEntity(subjectId = subject.id, title = "Class notes", note = "Mn²⁺ — العناصر")
        val pdf = SubjectResourceEntity(subjectId = subject.id, title = "Chapter.pdf", kind = "pdf", uri = "content://documents/chapter", mimeType = "application/pdf")
        val data = BackupData(listOf(subject), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(note, pdf))
        val decoded = RecallBackupCodec.decode(RecallBackupCodec.encode(data, UserSettings())) as BackupResult.Success
        assertEquals(listOf(note, pdf), decoded.data.resources)
    }
}
