package com.jakirulji.notes.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteRepository(private val dao: NoteDao) {
    fun activeNotes(): Flow<List<NoteEntity>> = dao.observeActive()
    fun trashedNotes(): Flow<List<NoteEntity>> = dao.observeTrash()

    suspend fun save(
        id: String?,
        title: String,
        content: String,
        color: Long,
        pinned: Boolean = false,
        tags: String? = null,
        folderId: String? = null
    ): String {
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.findById(it) }
        val stableId = existing?.id ?: id ?: UUID.randomUUID().toString()
        dao.upsert(
            NoteEntity(
                id = stableId,
                title = title.trim().ifBlank { "Untitled" },
                content = content,
                color = color,
                pinned = existing?.pinned ?: pinned,
                folderId = folderId ?: existing?.folderId,
                tags = tags ?: existing?.tags.orEmpty(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                trashedAt = null
            )
        )
        return stableId
    }

    suspend fun trash(id: String) = dao.moveToTrash(id, System.currentTimeMillis())
    suspend fun restore(id: String) = dao.restore(id, System.currentTimeMillis())
    suspend fun deleteForever(id: String) = dao.permanentlyDelete(id)
    suspend fun emptyTrash() = dao.emptyTrash()
    suspend fun togglePin(note: NoteEntity) = dao.setPinned(note.id, !note.pinned, System.currentTimeMillis())
}
