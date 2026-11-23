package com.jakirulji.notes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, FolderEntity::class], version = 2, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS folders (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderId ON notes(folderId)")
            }
        }

        @Volatile private var INSTANCE: NotesDatabase? = null

        fun getInstance(context: Context): NotesDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes.db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
