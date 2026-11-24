package com.jakirulji.notes.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class StoredAttachment(val relativePath: String, val displayName: String, val mimeType: String, val sizeBytes: Long)

/** Copies picked documents into app-private storage; only relative paths are persisted. */
object AttachmentStorage {
    suspend fun import(context: Context, source: Uri, noteId: String): StoredAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) { val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (index >= 0) cursor.getString(index) else null } else null
        }?.takeIf { it.isNotBlank() } ?: "Attachment"
        val mimeType = resolver.getType(source) ?: "application/octet-stream"
        val safeNoteId = noteId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "note" }
        val directory = File(context.filesDir, "attachments/$safeNoteId")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Unable to create attachment storage")
        val output = File(directory, UUID.randomUUID().toString())
        try {
            val input = resolver.openInputStream(source) ?: throw IllegalStateException("The selected file cannot be opened")
            input.use { inputStream -> FileOutputStream(output).use { outputStream -> inputStream.copyTo(outputStream); outputStream.fd.sync() } }
        } catch (error: Exception) { output.delete(); throw error }
        StoredAttachment("attachments/$safeNoteId/${output.name}", displayName, mimeType, output.length())
    }

    fun contentUri(context: Context, relativePath: String): Uri {
        val root = File(context.filesDir, "attachments").canonicalFile
        val file = File(context.filesDir, relativePath).canonicalFile
        require(file.toPath().startsWith(root.toPath())) { "Invalid attachment path" }
        require(file.isFile) { "Attachment file is missing" }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun delete(context: Context, relativePath: String) {
        val root = File(context.filesDir, "attachments").canonicalFile
        val file = File(context.filesDir, relativePath).canonicalFile
        if (file.toPath().startsWith(root.toPath())) { file.delete(); file.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete() }
    }
}