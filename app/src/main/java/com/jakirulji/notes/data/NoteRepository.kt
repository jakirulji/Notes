package com.jakirulji.notes.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteRepository(private val dao: NoteDao) {
    fun activeNotes(): Flow<List<NoteEntity>> = dao.observeActive()
    fun trashedNotes(): Flow<List<NoteEntity>> = dao.observeTrash()
    fun attachments(noteId: String): Flow<List<AttachmentEntity>> = dao.observeAttachments(noteId)
    fun checklistItems(noteId: String): Flow<List<ChecklistItemEntity>> = dao.observeChecklist(noteId)
    suspend fun checklistItemsOnce(noteId: String): List<ChecklistItemEntity> = dao.findChecklist(noteId)
    suspend fun addChecklistItem(noteId: String, text: String): String {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Checklist item cannot be empty" }
        val now = System.currentTimeMillis()
        val existing = dao.findChecklist(noteId)
        val id = UUID.randomUUID().toString()
        dao.upsertChecklistItem(ChecklistItemEntity(id, noteId, cleanText, false, (existing.maxOfOrNull { it.position } ?: -1) + 1, now, now))
        return id
    }
    suspend fun setChecklistChecked(id: String, checked: Boolean) = dao.setChecklistChecked(id, checked, System.currentTimeMillis())
    suspend fun deleteChecklistItem(id: String) = dao.deleteChecklistItem(id)
    suspend fun attachmentsOnce(noteId: String): List<AttachmentEntity> = dao.findAttachments(noteId)
    suspend fun trashAttachments(): List<AttachmentEntity> = dao.findTrashAttachments()

    suspend fun addAttachment(noteId: String, uri: String, name: String, mimeType: String, sizeBytes: Long) {
        dao.upsertAttachment(
            AttachmentEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                uri = uri,
                displayName = name,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteAttachment(id: String) = dao.deleteAttachment(id)

    suspend fun setReminder(id: String, reminderAt: Long?) =
        dao.setReminder(id, reminderAt, System.currentTimeMillis())

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
                trashedAt = null,
                reminderAt = existing?.reminderAt
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
