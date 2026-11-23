package com.jakirulji.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long = 0xFFE8E8D8L,
    val createdAt: Long,
    val updatedAt: Long
)
