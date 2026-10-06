package com.example.myapplication4

import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import java.io.*
import java.nio.file.Files
import java.util.zip.*
import org.junit.Assert.*
import org.junit.Test

class FullBackupArchiveTest {
    private val subject = SubjectEntity(id = "subject", name = "Physics")
    private val note = SubjectResourceEntity(id = "note", subjectId = subject.id, title = "Notes", note = "  قانون Ohm هو V = IR\n".repeat(1000))
    private val pdf = SubjectResourceEntity(id = "pdf", subjectId = subject.id, title = "كتاب.pdf", kind = "pdf", uri = "content://old/document", mimeType = "application/pdf")
    private fun data(resources: List<SubjectResourceEntity>) = BackupData(listOf(subject), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), resources)
    @Test fun allDataArchivePreservesActualBytesUnicodeNotesAndSettings() {
        val photo = pdf.copy(id = "photo", kind = "photo", uri = "content://old/photo", mimeType = "image/png")
        val payload = ByteArray(100_000) { (it % 255).toByte() }
        val output = ByteArrayOutputStream()
        FullBackupArchive.write(output, data(listOf(note, pdf, photo)), UserSettings(debugMode = true)) { payload.inputStream() }
        val directory = Files.createTempDirectory("recall-archive-test").toFile()
        try {
            val restored = FullBackupArchive.read(output.toByteArray().inputStream(), directory)
            assertEquals(note.note, restored.data.resources.first().note)
            assertEquals(2, restored.files.size)
            restored.files.values.forEach { assertArrayEquals(payload, it.readBytes()) }
            assertTrue(restored.settings.debugMode)
        } finally { directory.deleteRecursively() }
    }
    @Test fun inaccessibleAttachmentFailsInsteadOfSilentlyOmittingIt() {
        try {
            FullBackupArchive.write(ByteArrayOutputStream(), data(listOf(pdf)), UserSettings()) { throw FileNotFoundException() }
            fail("Expected missing attachment error")
        } catch (e: IOException) { assertTrue(e.message!!.contains(pdf.title)) }
    }
    @Test fun zipTraversalAndMissingManifestAreRejectedAndCleaned() {
        for (name in listOf("../escaped", "/absolute", "materials/0")) {
            val zip = ByteArrayOutputStream()
            ZipOutputStream(zip).use { it.putNextEntry(ZipEntry(name)); it.write(byteArrayOf(1)); it.closeEntry() }
            val directory = Files.createTempDirectory("recall-invalid-test").toFile()
            try {
                assertThrows(Exception::class.java) { FullBackupArchive.read(zip.toByteArray().inputStream(), directory) }
                assertTrue(directory.listFiles()!!.isEmpty())
            } finally { directory.deleteRecursively() }
        }
    }
    @Test fun modifiedFileFailsManifestChecksum() {
        val zip = ByteArrayOutputStream()
        FullBackupArchive.write(zip, data(listOf(pdf)), UserSettings()) { byteArrayOf(1, 2, 3).inputStream() }
        val corrupted = ByteArrayOutputStream()
        ZipOutputStream(corrupted).use { output ->
            ZipInputStream(zip.toByteArray().inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    output.putNextEntry(ZipEntry(entry.name))
                    val bytes = input.readBytes()
                    output.write(if (entry.name.startsWith("materials/")) byteArrayOf(4, 5, 6) else bytes)
                    output.closeEntry()
                }
            }
        }
        val directory = Files.createTempDirectory("recall-checksum-test").toFile()
        try { assertThrows(Exception::class.java) { FullBackupArchive.read(corrupted.toByteArray().inputStream(), directory) } }
        finally { directory.deleteRecursively() }
    }
}
