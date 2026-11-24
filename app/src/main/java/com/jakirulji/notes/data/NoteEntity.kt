package com.jakirulji.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val color: Long,
    val pinned: Boolean = false,
    val folderId: String? = null,
    val tags: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long? = null,
    val reminderAt: Long? = null
)
