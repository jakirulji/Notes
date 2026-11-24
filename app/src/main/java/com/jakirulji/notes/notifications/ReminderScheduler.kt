package com.jakirulji.notes.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ReminderScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(noteId: String, title: String, reminderAt: Long?) {
        val uniqueName = workName(noteId)
        if (reminderAt == null || reminderAt <= System.currentTimeMillis()) {
            workManager.cancelUniqueWork(uniqueName)
            return
        }
        val delay = reminderAt - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<NoteReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(NoteReminderWorker.KEY_NOTE_ID, noteId)
                    .putString(NoteReminderWorker.KEY_TITLE, title)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(noteId: String) {
        workManager.cancelUniqueWork(workName(noteId))
    }

    private fun workName(noteId: String) = "note-reminder-$noteId"
}
