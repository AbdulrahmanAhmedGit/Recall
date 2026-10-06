package com.example.myapplication4.domain

import com.example.myapplication4.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.zip.*

/** Streaming, versioned portable archive. No supplied path is used as a filesystem path. */
object FullBackupArchive {
    private const val MAX_FILE = 512L * 1024 * 1024
    private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private const val MAX_JSON = 50L * 1024 * 1024
    data class Restored(val data: BackupData, val settings: UserSettings, val files: Map<String, File>)

    fun write(output: OutputStream, data: BackupData, settings: UserSettings, open: (String) -> InputStream) {
        val manifest = JSONArray()
        val replacements = mutableMapOf<String, String>()
        var total = 0L
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            data.resources.filter { it.uri != null }.forEachIndexed { index, resource ->
                require(index < 100_000) { "Too many attachments" }
                val entry = "materials/" + index
                val digest = MessageDigest.getInstance("SHA-256")
                zip.putNextEntry(ZipEntry(entry))
                val bytes = try {
                    open(requireNotNull(resource.uri)).use { input ->
                        transfer(input, zip, MAX_FILE) { buffer, count ->
                            total += count
                            require(total <= MAX_TOTAL) { "Archive exceeds 2 GB" }
                            digest.update(buffer, 0, count)
                        }
                    }
                } catch (e: Exception) { throw IOException("Cannot export attachment: " + resource.title + ". Check that the original file is accessible. " + e.message, e) }
                zip.closeEntry()
                manifest.put(JSONObject().put("resource_id", resource.id).put("entry", entry).put("bytes", bytes).put("sha256", hex(digest.digest())))
                replacements[resource.id] = "recall-archive:" + entry
            }
            val portable = data.copy(resources = data.resources.map { it.copy(uri = replacements[it.id] ?: it.uri) })
            fun jsonEntry(name: String, content: String) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_JSON)
                total += bytes.size
                require(total <= MAX_TOTAL) { "Archive exceeds 2 GB" }
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            jsonEntry("data.json", RecallBackupCodec.encode(portable, settings))
            jsonEntry("manifest.json", JSONObject().put("format", "recall-full-backup").put("version", 1).put("files", manifest).toString())
        }
    }

    fun read(input: InputStream, directory: File): Restored {
        require(directory.isDirectory && directory.listFiles().orEmpty().isEmpty()) { "Restore directory must be empty" }
        val seen = mutableSetOf<String>()
        val files = mutableMapOf<String, File>()
        val hashes = mutableMapOf<String, String>()
        var total = 0L
        var dataText: String? = null
        var manifestText: String? = null
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && seen.add(name) && seen.size <= 100_002) { "Duplicate or invalid archive entry" }
                    if (name == "data.json" || name == "manifest.json") {
                        val bytes = ByteArrayOutputStream()
                        transfer(zip, bytes, MAX_JSON) { _, count -> total += count; require(total <= MAX_TOTAL) }
                        val text = bytes.toString("UTF-8")
                        if (name == "data.json") dataText = text else manifestText = text
                    } else {
                        require(Regex("materials/[0-9]{1,6}").matches(name)) { "Unsafe archive path" }
                        val file = File(directory, name.substringAfter('/'))
                        val hash = MessageDigest.getInstance("SHA-256")
                        file.outputStream().use { out ->
                            transfer(zip, out, MAX_FILE) { bytes, count -> total += count; require(total <= MAX_TOTAL); hash.update(bytes, 0, count) }
                        }
                        files[name] = file
                        hashes[name] = hex(hash.digest())
                    }
                    zip.closeEntry()
                }
            }
            val manifest = JSONObject(requireNotNull(manifestText) { "Missing manifest" })
            require(manifest.getString("format") == "recall-full-backup" && manifest.getInt("version") == 1)
            val decoded = RecallBackupCodec.decode(requireNotNull(dataText)) as? BackupResult.Success ?: error("Invalid study data")
            val metadata = manifest.getJSONArray("files")
            val resources = decoded.data.resources.associateBy { it.id }
            require(resources.size == decoded.data.resources.size)
            val byResource = mutableMapOf<String, File>()
            val consumed = mutableSetOf<String>()
            for (i in 0 until metadata.length()) {
                val item = metadata.getJSONObject(i)
                val id = item.getString("resource_id")
                val entry = item.getString("entry")
                val file = requireNotNull(files[entry])
                require(consumed.add(entry) && !byResource.containsKey(id))
                require(file.length() == item.getLong("bytes") && hashes[entry] == item.getString("sha256")) { "Attachment checksum mismatch" }
                require(resources[id]?.uri == "recall-archive:" + entry) { "Invalid attachment mapping" }
                byResource[id] = file
            }
            require(consumed == files.keys)
            require(decoded.data.resources.filter { it.uri != null }.all { it.id in byResource }) { "Missing attachments" }
            return Restored(decoded.data, decoded.settings, byResource)
        } catch (e: Exception) {
            // Only files created in this call inside the fresh private directory are removed.
            directory.listFiles().orEmpty().forEach { it.delete() }
            throw e
        }
    }

    private fun transfer(input: InputStream, output: OutputStream, limit: Long, consume: (ByteArray, Int) -> Unit): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            require(total <= limit) { "Attachment or metadata is too large" }
            consume(buffer, count)
            output.write(buffer, 0, count)
        }
        return total
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
