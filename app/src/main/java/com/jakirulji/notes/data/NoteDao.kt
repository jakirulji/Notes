package com.jakirulji.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE trashedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashedAt IS NULL AND folderId = :folderId ORDER BY pinned DESC, updatedAt DESC")
    fun observeFolder(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC, createdAt ASC")
    fun observeChecklist(noteId: String): Flow<List<ChecklistItemEntity>>
    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC, createdAt ASC")
    suspend fun findChecklist(noteId: String): List<ChecklistItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItem(item: ChecklistItemEntity)
    @Query("UPDATE checklist_items SET checked = :checked, updatedAt = :time WHERE id = :id")
    suspend fun setChecklistChecked(id: String, checked: Boolean, time: Long)
    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteChecklistItem(id: String)

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY addedAt")
    fun observeAttachments(noteId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId")
    suspend fun findAttachments(noteId: String): List<AttachmentEntity>

    @Query("SELECT attachments.* FROM attachments INNER JOIN notes ON notes.id = attachments.noteId WHERE notes.trashedAt IS NOT NULL")
    suspend fun findTrashAttachments(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: String)

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET trashedAt = :time, updatedAt = :time WHERE id = :id AND trashedAt IS NULL")
    suspend fun moveToTrash(id: String, time: Long)

    @Query("UPDATE notes SET trashedAt = NULL, updatedAt = :time WHERE id = :id")
    suspend fun restore(id: String, time: Long)

    @Query("DELETE FROM notes WHERE id = :id AND trashedAt IS NOT NULL")
    suspend fun permanentlyDelete(id: String)

    @Query("DELETE FROM notes WHERE trashedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("UPDATE notes SET reminderAt = :reminderAt, updatedAt = :time WHERE id = :id")
    suspend fun setReminder(id: String, reminderAt: Long?, time: Long)

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :time WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, time: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :time WHERE id = :noteId")
    suspend fun moveToFolder(noteId: String, folderId: String?, time: Long)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachFolder(folderId: String)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun findFolder(id: String): FolderEntity?
}
