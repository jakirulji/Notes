package com.jakirulji.notes.data
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId")]
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val text: String,
    val checked: Boolean = false,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long
)
