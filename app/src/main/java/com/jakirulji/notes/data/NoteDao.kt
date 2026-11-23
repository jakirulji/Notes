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

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

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

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :time WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, time: Long)
}
