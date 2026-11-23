package com.jakirulji.notes.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FolderRepository(private val dao: NoteDao) {
    fun folders(): Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun create(name: String, color: Long = 0xFFE8E8D8L): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Folder name cannot be empty" }
        val now = System.currentTimeMillis()
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            color = color,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertFolder(folder)
        return folder.id
    }

    suspend fun rename(id: String, name: String) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Folder name cannot be empty" }
        val current = dao.findFolder(id) ?: return
        dao.upsertFolder(current.copy(name = cleanName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        dao.detachFolder(id)
        dao.deleteFolder(id)
    }

    suspend fun moveNote(noteId: String, folderId: String?) {
        if (folderId != null && dao.findFolder(folderId) == null) return
        dao.moveToFolder(noteId, folderId, System.currentTimeMillis())
    }
}
