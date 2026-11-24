package com.jakirulji.notes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedAt: Long
)
