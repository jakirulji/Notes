package com.jakirulji.notes.storage
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/** Records AAC audio in an app-private M4A file. Call start/stop on the main thread. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    fun start(noteId: String) {
        check(recorder == null) { "A recording is already in progress" }
        val safeNoteId = noteId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "note" }
        val directory = File(context.filesDir, "attachments/$safeNoteId")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Unable to create audio storage")
        val file = File(directory, "${UUID.randomUUID()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(96000)
            mediaRecorder.setAudioSamplingRate(44100)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            output = file
        } catch (error: Exception) {
            try { mediaRecorder.reset() } catch (_: Exception) { }
            mediaRecorder.release()
            file.delete()
            throw error
        }
    }
    fun stop(): File? {
        val active = recorder ?: return null
        val file = output
        return try {
            active.stop()
            file?.takeIf { it.exists() && it.length() > 0L }
        } catch (_: RuntimeException) {
            file?.delete()
            null
        } finally {
            active.reset()
            active.release()
            recorder = null
            output = null
        }
    }
    fun cancel() {
        val active = recorder ?: return
        val file = output
        try { active.stop() } catch (_: RuntimeException) { }
        active.reset()
        active.release()
        recorder = null
        output = null
        file?.delete()
    }
}
