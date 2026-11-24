package com.jakirulji.notes

import com.jakirulji.notes.data.NoteDao
import com.jakirulji.notes.data.AttachmentEntity
import com.jakirulji.notes.data.NoteEntity
import com.jakirulji.notes.data.FolderEntity
import com.jakirulji.notes.data.FolderRepository
import com.jakirulji.notes.data.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRepositoryTest {
    @Test
    fun saveCreatesPersistentNoteWithNormalizedTitle() = runBlocking {
        val dao = FakeNoteDao()
        val repository = NoteRepository(dao)

        repository.save(null, "   ", "First thought", 0xFFE8E8D8L)

        val saved = dao.items.value.single()
        assertNotNull(saved.id)
        assertEquals("Untitled", saved.title)
        assertEquals("First thought", saved.content)
        assertNull(saved.trashedAt)
    }

    @Test
    fun repeatedAutosavesWithDraftIdUpdateOneNote() = runBlocking {
        val dao = FakeNoteDao()
        val repository = NoteRepository(dao)
        val draftId = "editor-draft-id"

        val firstId = repository.save(draftId, "Draft", "First", 0xFFE8E8D8L)
        val secondId = repository.save(draftId, "Draft", "Second", 0xFFE8E8D8L)

        assertEquals(draftId, firstId)
        assertEquals(firstId, secondId)
        assertEquals(1, dao.items.value.size)
        assertEquals("Second", dao.items.value.single().content)
    }

    @Test
    fun attachmentsAreScopedToTheirNoteAndCanBeRemoved() = runBlocking {
        val dao = FakeNoteDao()
        val repository = NoteRepository(dao)
        repository.save("note-a", "A", "", 0xFFE8E8D8L)
        repository.save("note-b", "B", "", 0xFFE8E8D8L)

        repository.addAttachment("note-a", "content://provider/file/1", "scan.pdf", "application/pdf", 1200L)

        val attachment = dao.attachments.value.single()
        assertEquals("note-a", attachment.noteId)
        assertEquals("scan.pdf", attachment.displayName)
        assertEquals(1, repository.attachmentsOnce("note-a").size)
        assertTrue(repository.attachmentsOnce("note-b").isEmpty())
        repository.deleteAttachment(attachment.id)
        assertTrue(dao.attachments.value.isEmpty())
    }

    @Test
    fun reminderCanBeSetAndCleared() = runBlocking {
        val dao = FakeNoteDao()
        val repository = NoteRepository(dao)
        repository.save("reminder-note", "Exam", "", 0xFFE8E8D8L)

        repository.setReminder("reminder-note", 123456L)
        assertEquals(123456L, dao.items.value.single().reminderAt)

        repository.setReminder("reminder-note", null)
        assertNull(dao.items.value.single().reminderAt)
    }

    @Test
    fun updatePreservesIdentityCreationTimeAndPinState() = runBlocking {
        val dao = FakeNoteDao()
        val repository = NoteRepository(dao)
        val original = NoteEntity(
            id = "stable-id", title = "Old", content = "Body", color = 0xFFE8E8D8L,
            pinned = true, createdAt = 10L, updatedAt = 10L
        )
        dao.upsert(original)

        repository.save(original.id, "Updated", "New body", original.color)

        val updated = dao.items.value.single()
        assertEquals("stable-id", updated.id)
        assertEquals(10L, updated.createdAt)
        assertTrue(updated.pinned)
        assertEquals("Updated", updated.title)
        assertEquals("New body", updated.content)
    }

    @Test
    fun autosavePreservesExistingReminder() = runBlocking {
        val dao = FakeNoteDao()
        val repository = NoteRepository(dao)
        val original = NoteEntity(
            id = "reminder-preserve", title = "Exam", content = "Review",
            color = 0xFFE8E8D8L, createdAt = 1L, updatedAt = 1L, reminderAt = 987654L
        )
        dao.upsert(original)

        repository.save(original.id, "Exam update", "Review again", original.color)

        assertEquals(987654L, dao.items.value.single().reminderAt)
    }

    @Test
    fun trashRestoreAndPermanentDeleteHaveDistinctSemantics() = runBlocking {
        val dao = FakeNoteDao()
        val repository = NoteRepository(dao)
        repository.save(null, "Keep me", "", 0xFFE8E8D8L)
        val id = dao.items.value.single().id

        repository.trash(id)
        assertNotNull(dao.items.value.single().trashedAt)

        repository.restore(id)
        assertNull(dao.items.value.single().trashedAt)

        repository.trash(id)
        repository.deleteForever(id)
        assertTrue(dao.items.value.isEmpty())
    }

    @Test
    fun folderLifecycleMovesNotesAndDetachesOnDelete() = runBlocking {
        val dao = FakeNoteDao()
        val notes = NoteRepository(dao)
        val folders = FolderRepository(dao)
        notes.save(null, "Folder note", "", 0xFFE8E8D8L)
        val noteId = dao.items.value.single().id

        val folderId = folders.create("Study")
        folders.moveNote(noteId, folderId)
        assertEquals(folderId, dao.items.value.single().folderId)

        folders.rename(folderId, "Medical")
        assertEquals("Medical", dao.findFolder(folderId)?.name)

        folders.delete(folderId)
        assertNull(dao.items.value.single().folderId)
    }

    private class FakeNoteDao : NoteDao {
        val items = MutableStateFlow<List<NoteEntity>>(emptyList())
        private val folders = MutableStateFlow<List<FolderEntity>>(emptyList())
        val attachments = MutableStateFlow<List<AttachmentEntity>>(emptyList())

        override fun observeAttachments(noteId: String): Flow<List<AttachmentEntity>> =
            MutableStateFlow(attachments.value.filter { it.noteId == noteId })

        override suspend fun findAttachments(noteId: String): List<AttachmentEntity> = attachments.value.filter { it.noteId == noteId }

        override suspend fun findTrashAttachments(): List<AttachmentEntity> {
            val trashedIds = items.value.filter { it.trashedAt != null }.map { it.id }.toSet()
            return attachments.value.filter { it.noteId in trashedIds }
        }

        override suspend fun upsertAttachment(attachment: AttachmentEntity) {
            attachments.value = attachments.value.filterNot { it.id == attachment.id } + attachment
        }

        override suspend fun deleteAttachment(id: String) {
            attachments.value = attachments.value.filterNot { it.id == id }
        }

        override fun observeActive(): Flow<List<NoteEntity>> =
            MutableStateFlow(items.value.filter { it.trashedAt == null })

        override fun observeTrash(): Flow<List<NoteEntity>> =
            MutableStateFlow(items.value.filter { it.trashedAt != null })

        override fun observeFolder(folderId: String): Flow<List<NoteEntity>> =
            MutableStateFlow(items.value.filter { it.folderId == folderId && it.trashedAt == null })

        override fun observeFolders(): Flow<List<FolderEntity>> = folders

        override suspend fun upsertFolder(folder: FolderEntity) {
            folders.value = folders.value.filterNot { it.id == folder.id } + folder
        }

        override suspend fun deleteFolder(id: String) {
            folders.value = folders.value.filterNot { it.id == id }
        }

        override suspend fun findFolder(id: String): FolderEntity? =
            folders.value.firstOrNull { it.id == id }

        override suspend fun findById(id: String): NoteEntity? =
            items.value.firstOrNull { it.id == id }

        override suspend fun upsert(note: NoteEntity) {
            items.value = items.value.filterNot { it.id == note.id } + note
        }

        override suspend fun moveToTrash(id: String, time: Long) {
            items.value = items.value.map {
                if (it.id == id && it.trashedAt == null) it.copy(trashedAt = time, updatedAt = time) else it
            }
        }

        override suspend fun restore(id: String, time: Long) {
            items.value = items.value.map {
                if (it.id == id) it.copy(trashedAt = null, updatedAt = time) else it
            }
        }

        override suspend fun permanentlyDelete(id: String) {
            items.value = items.value.filterNot { it.id == id && it.trashedAt != null }
        }

        override suspend fun emptyTrash() {
            items.value = items.value.filter { it.trashedAt == null }
        }

        override suspend fun setReminder(id: String, reminderAt: Long?, time: Long) {
            items.value = items.value.map {
                if (it.id == id) it.copy(reminderAt = reminderAt, updatedAt = time) else it
            }
        }

        override suspend fun setPinned(id: String, pinned: Boolean, time: Long) {
            items.value = items.value.map {
                if (it.id == id) it.copy(pinned = pinned, updatedAt = time) else it
            }
        }

        override suspend fun moveToFolder(noteId: String, folderId: String?, time: Long) {
            items.value = items.value.map {
                if (it.id == noteId) it.copy(folderId = folderId, updatedAt = time) else it
            }
        }

        override suspend fun detachFolder(folderId: String) {
            items.value = items.value.map {
                if (it.folderId == folderId) it.copy(folderId = null) else it
            }
        }
    }
}
