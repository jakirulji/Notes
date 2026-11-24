package com.jakirulji.notes

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jakirulji.notes.data.NoteEntity
import com.jakirulji.notes.data.ChecklistItemEntity
import com.jakirulji.notes.data.FolderRepository
import com.jakirulji.notes.data.NoteRepository
import com.jakirulji.notes.data.NotesDatabase
import com.jakirulji.notes.notifications.ReminderScheduler
import com.jakirulji.notes.storage.AttachmentStorage
import com.jakirulji.notes.storage.VoiceRecorder
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.Calendar
import java.text.DateFormat
import java.util.Date

private val Ink = Color(0xFF20211F)
private val Paper = Color(0xFFF7F6F1)
private val Accent = Color(0xFF667B63)
private const val DEFAULT_NOTE_COLOR = 0xFFE8E8D8L
private const val HTML_CONTENT_PREFIX = "<!--notes-html-v1-->"

private fun notePlainText(content: String): String =
    if (content.startsWith(HTML_CONTENT_PREFIX)) {
        Html.fromHtml(content.removePrefix(HTML_CONTENT_PREFIX), Html.FROM_HTML_MODE_LEGACY).toString()
    } else content

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = NotesDatabase.getInstance(applicationContext).noteDao()
        val repository = NoteRepository(dao)
        val folderRepository = FolderRepository(dao)
        val reminderScheduler = ReminderScheduler(applicationContext)
        setContent { NotesTheme { NotesApp(repository, folderRepository, reminderScheduler) } }
    }
}

@Composable
private fun NotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Accent, onPrimary = Color.White, background = Paper,
            surface = Color.White, onSurface = Ink, secondary = Color(0xFFD7B98E)),
        content = content
    )
}

@Composable
private fun NotesApp(repository: NoteRepository, folderRepository: FolderRepository, reminderScheduler: ReminderScheduler) {
    val notes by repository.activeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val trash by repository.trashedNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val folders by folderRepository.folders().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val persistenceMutex = remember { Mutex() }
    var query by remember { mutableStateOf("") }
    var showTrash by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var createFolderOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val visible = (if (showTrash) trash else notes).filter {
        (it.title + " " + notePlainText(it.content) + " " + it.tags).contains(query, ignoreCase = true) &&
            (showTrash || selectedFolderId == null || it.folderId == selectedFolderId)
    }

    Scaffold(
        containerColor = Paper,
        floatingActionButton = {
            if (!showTrash) ExtendedFloatingActionButton(
                onClick = { editing = null; editorOpen = true },
                containerColor = Ink, contentColor = Color.White, shape = RoundedCornerShape(18.dp)
            ) { Text("+", fontSize = 22.sp); Spacer(Modifier.width(8.dp)); Text("New note") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (showTrash) "YOUR ARCHIVE" else "YOUR SPACE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(if (showTrash) "Trash" else "Notes", fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Box(Modifier.size(42.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("✦", color = Accent, fontSize = 20.sp) }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showTrash, onClick = { showTrash = false; query = "" }, label = { Text("Notes") })
                FilterChip(selected = showTrash, onClick = { showTrash = true; query = "" }, label = { Text("Trash") })
                Spacer(Modifier.weight(1f))
                if (showTrash && trash.isNotEmpty()) TextButton(onClick = {
                    trash.forEach { reminderScheduler.cancel(it.id) }
                    scope.launch {
                        repository.trashAttachments().forEach { AttachmentStorage.delete(context, it.uri) }
                        repository.emptyTrash()
                    }
                }) { Text("Empty") }
            }
            if (!showTrash) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = selectedFolderId == null, onClick = { selectedFolderId = null }, label = { Text("All folders") })
                    folders.forEach { folder -> FilterChip(selected = selectedFolderId == folder.id, onClick = { selectedFolderId = folder.id }, label = { Text(folder.name) }) }
                    AssistChip(onClick = { newFolderName = ""; createFolderOpen = true }, label = { Text("+ Folder") })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your thoughts") }, leadingIcon = { Text("⌕", fontSize = 24.sp) },
                shape = RoundedCornerShape(18.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = Accent)
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (showTrash) "Recently deleted" else "Recent notes", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.weight(1f)); Text("${visible.size} ITEMS", color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(if (showTrash) "Your trash is empty" else "A clear space for your next thought.", color = Color.Gray) }
            } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(visible, key = { it.id }) { note ->
                    NoteCard(
                        note, showTrash, folders,
                        onOpen = { if (!showTrash) { editing = note; editorOpen = true } },
                        onMoveFolder = { folderId -> scope.launch { folderRepository.moveNote(note.id, folderId) } },
                        onPin = { scope.launch { repository.togglePin(note) } },
                        onTrash = { scope.launch { repository.trash(note.id); reminderScheduler.cancel(note.id) } },
                        onRestore = { scope.launch { repository.restore(note.id) } },
                        onDeleteForever = { scope.launch {
                            repository.attachmentsOnce(note.id).forEach { AttachmentStorage.delete(context, it.uri) }
                            repository.deleteForever(note.id); reminderScheduler.cancel(note.id)
                        } }
                    )
                }
            }
        }
    }

    if (createFolderOpen) AlertDialog(
        onDismissRequest = { createFolderOpen = false },
        title = { Text("Create folder") },
        text = { OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, singleLine = true, placeholder = { Text("Folder name") }) },
        confirmButton = { TextButton(onClick = {
            val name = newFolderName.trim()
            if (name.isNotEmpty()) scope.launch { folderRepository.create(name) }
            createFolderOpen = false
        }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { createFolderOpen = false }) { Text("Cancel") } }
    )

    if (editorOpen) NoteEditor(
        note = editing,
        onClose = { editorOpen = false },
        onSave = { id, title, body, tags, reminderAt, color ->
            persistenceMutex.withLock {
                repository.save(id, title, body, color, tags = tags, folderId = editing?.folderId ?: selectedFolderId)
                repository.setReminder(id, reminderAt)
                reminderScheduler.schedule(id, title.ifBlank { "Untitled" }, reminderAt)
            }
        },
        onAttach = { id, title, body, tags, uri, name, mimeType, sizeBytes, color ->
            persistenceMutex.withLock {
                repository.save(id, title, body, color, tags = tags, folderId = editing?.folderId ?: selectedFolderId)
                repository.addAttachment(id, uri, name, mimeType, sizeBytes)
            }
        },
        attachmentsFlow = { repository.attachments(it) },
        checklistFlow = { repository.checklistItems(it) },
        onAddChecklistItem = { id, title, body, tags, color, itemText ->
            persistenceMutex.withLock {
                repository.save(id, title, body, color, tags = tags, folderId = editing?.folderId ?: selectedFolderId)
                repository.addChecklistItem(id, itemText)
            }
        },
        onToggleChecklistItem = { item, checked -> scope.launch { repository.setChecklistChecked(item.id, checked) } },
        onDeleteChecklistItem = { item -> scope.launch { repository.deleteChecklistItem(item.id) } },
        onDeleteAttachment = { attachmentId -> scope.launch { repository.deleteAttachment(attachmentId) } }
    )
}

@Composable
private fun NoteCard(
    note: NoteEntity, inTrash: Boolean, folders: List<com.jakirulji.notes.data.FolderEntity>,
    onOpen: () -> Unit, onMoveFolder: (String?) -> Unit, onPin: () -> Unit,
    onTrash: () -> Unit, onRestore: () -> Unit, onDeleteForever: () -> Unit
) {
    var folderMenuOpen by remember(note.id) { mutableStateOf(false) }
    Surface(onClick = onOpen, color = Color(note.color.toInt()), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (note.pinned && !inTrash) Text("PIN", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            val preview = notePlainText(note.content)
            if (preview.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(preview, color = Ink.copy(alpha = .72f), fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4) }
            note.reminderAt?.let { Spacer(Modifier.height(6.dp)); Text("REMINDER · " + DateFormat.getDateTimeInstance().format(Date(it)), color = Accent, fontSize = 10.sp) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (inTrash) {
                    TextButton(onClick = onRestore) { Text("Restore") }
                    TextButton(onClick = onDeleteForever) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = onPin) { Text(if (note.pinned) "Unpin" else "Pin") }
                    Box {
                        TextButton(onClick = { folderMenuOpen = true }) { Text("Folder") }
                        DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("No folder") }, onClick = { onMoveFolder(null); folderMenuOpen = false })
                            folders.forEach { folder -> DropdownMenuItem(text = { Text(folder.name) }, onClick = { onMoveFolder(folder.id); folderMenuOpen = false }) }
                        }
                    }
                    Spacer(Modifier.weight(1f)); TextButton(onClick = onTrash) { Text("Move to trash") }
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(
    note: NoteEntity?,
    onClose: () -> Unit,
    onSave: suspend (String, String, String, String, Long?, Long) -> Unit,
    onAttach: suspend (String, String, String, String, String, String, String, Long, Long) -> Unit,
    attachmentsFlow: (String) -> kotlinx.coroutines.flow.Flow<List<com.jakirulji.notes.data.AttachmentEntity>>,
    onDeleteAttachment: (String) -> Unit,
    checklistFlow: (String) -> kotlinx.coroutines.flow.Flow<List<ChecklistItemEntity>>,
    onAddChecklistItem: suspend (String, String, String, String, Long, String) -> Unit,
    onToggleChecklistItem: (ChecklistItemEntity, Boolean) -> Unit,
    onDeleteChecklistItem: (ChecklistItemEntity) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val draftId = rememberSaveable(note?.id) { note?.id ?: UUID.randomUUID().toString() }
    var title by rememberSaveable(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    val richTextState = rememberRichTextState()
    LaunchedEffect(note?.id, note?.content) {
        val stored = note?.content.orEmpty()
        if (stored.startsWith(HTML_CONTENT_PREFIX)) richTextState.setHtml(stored.removePrefix(HTML_CONTENT_PREFIX))
        else richTextState.setMarkdown(stored)
    }
    val body = HTML_CONTENT_PREFIX + richTextState.toHtml()
    var tags by rememberSaveable(note?.id) { mutableStateOf(note?.tags.orEmpty()) }
    var reminderAt by rememberSaveable(note?.id) { mutableStateOf(note?.reminderAt) }
    var selectedColor by rememberSaveable(note?.id) { mutableLongStateOf(note?.color ?: DEFAULT_NOTE_COLOR) }
    var permissionDenied by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var checklistDraft by rememberSaveable(note?.id) { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    val voiceRecorder = remember(draftId) { VoiceRecorder(context) }
    val checklist by remember(draftId) { checklistFlow(draftId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val attachments by remember(draftId) { attachmentsFlow(draftId) }.collectAsStateWithLifecycle(initialValue = emptyList())

    suspend fun persistDraft() {
        if (title.isNotBlank() || richTextState.annotatedString.text.isNotBlank() || tags.isNotBlank()) {
            onSave(draftId, title.ifBlank { "Untitled" }, body,
                tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", "),
                reminderAt, selectedColor)
        }
    }

    fun attachUri(uri: Uri) {
        scope.launch {
            attachmentError = null
            try {
                val stored = AttachmentStorage.import(context, uri, draftId)
                persistDraft()
                onAttach(draftId, title.ifBlank { "Untitled" }, body,
                    tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", "),
                    stored.relativePath, stored.displayName, stored.mimeType, stored.sizeBytes, selectedColor)
            } catch (error: Exception) { attachmentError = error.message ?: "Unable to save this attachment." }
        }
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) attachUri(uri) }

    fun startVoiceRecording() {
        try { voiceRecorder.start(draftId); isRecording = true; voiceError = null }
        catch (error: Exception) { voiceError = error.message ?: "Could not start recording."; isRecording = false }
    }
    fun stopVoiceRecording() {
        val file = voiceRecorder.stop()
        isRecording = false
        if (file != null) scope.launch {
            persistDraft()
            onAttach(draftId, title.ifBlank { "Untitled" }, body,
                tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", "),
                file.relativeTo(context.filesDir).invariantSeparatorsPath, "Voice note.m4a", "audio/mp4", file.length(), selectedColor)
        } else voiceError = "Recording was too short or could not be saved."
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecording() else voiceError = "Microphone permission is required to record audio."
    }
    DisposableEffect(voiceRecorder) { onDispose { voiceRecorder.cancel() } }

    var pendingReminder by remember { mutableStateOf<Long?>(null) }
    fun applyReminder(timestamp: Long) {
        reminderAt = timestamp; pendingReminder = null
        scope.launch { persistDraft() }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingReminder?.let(::applyReminder) else { permissionDenied = true; pendingReminder = null }
    }
    fun chooseReminder() {
        val now = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, day ->
            val selected = Calendar.getInstance().apply { set(year, month, day) }
            TimePickerDialog(context, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0)
                if (selected.timeInMillis > System.currentTimeMillis()) {
                    val stamp = selected.timeInMillis
                    if (Build.VERSION.SDK_INT >= 33) { pendingReminder = stamp; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    else applyReminder(stamp)
                }
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    LaunchedEffect(draftId, title, body, tags, reminderAt, selectedColor) {
        if (title.isNotBlank() || richTextState.annotatedString.text.isNotBlank() || tags.isNotBlank()) {
            delay(700); persistDraft()
        }
    }

    Dialog(
        onDismissRequest = { scope.launch { persistDraft(); onClose() } },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(selectedColor.toInt()), shape = RoundedCornerShape(0.dp)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { persistDraft(); onClose() } }) { Text("Close") }
                    Text(if (note == null) "New note" else "Edit note", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    TextButton(onClick = { scope.launch { persistDraft(); onClose() } }) { Text("Done", fontWeight = FontWeight.Bold) }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Title") }, singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent, focusedBorderColor = Accent)
                    )
                    Text("Page color", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = .65f))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFFE8E8D8L, 0xFFFFE7C2L, 0xFFF8D9DCL, 0xFFDCEAF8L, 0xFFE2DDF7L, 0xFFD9EEE5L, 0xFFF0E2D2L).forEach { color ->
                            FilterChip(selected = selectedColor == color, onClick = { selectedColor = color },
                                label = { Text("●", color = Color(color.toInt())) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White.copy(alpha = .65f), containerColor = Color.White.copy(alpha = .35f)))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Text color", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = .65f))
                        Spacer(Modifier.width(8.dp))
                        listOf(Color(0xFF20211F), Color(0xFFB3261E), Color(0xFF2457A6), Color(0xFF26734D), Color(0xFF7B3FA1), Color(0xFFB36A00)).forEach { color ->
                            Box(Modifier.padding(horizontal = 4.dp).size(28.dp).background(color, CircleShape)
                                .clickable { richTextState.toggleSpanStyle(SpanStyle(color = color)) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }) { Text("B", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }) { Text("I", fontStyle = FontStyle.Italic) }
                        TextButton(onClick = { richTextState.toggleUnorderedList() }) { Text("• List") }
                        TextButton(onClick = { richTextState.toggleOrderedList() }) { Text("1. List") }
                        TextButton(onClick = { richTextState.toggleCodeSpan() }) { Text("Code") }
                    }
                    RichTextEditor(state = richTextState, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), placeholder = { Text("Start writing…") })
                    HorizontalDivider(color = Ink.copy(alpha = .12f))
                    Text("Checklist", fontWeight = FontWeight.SemiBold, color = Ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = checklistDraft, onValueChange = { checklistDraft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Add a task") }, singleLine = true)
                        TextButton(enabled = checklistDraft.isNotBlank(), onClick = {
                            val task = checklistDraft.trim()
                            if (task.isNotEmpty()) {
                                scope.launch {
                                    persistDraft()
                                    onAddChecklistItem(draftId, title.ifBlank { "Untitled" }, body,
                                        tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", "), selectedColor, task)
                                }
                                checklistDraft = ""
                            }
                        }) { Text("Add") }
                    }
                    checklist.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.checked, onCheckedChange = { onToggleChecklistItem(item, it) })
                            Text(item.text, Modifier.weight(1f), color = if (item.checked) Color.Gray else Ink)
                            TextButton(onClick = { onDeleteChecklistItem(item) }) { Text("Remove") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Voice note", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = Ink)
                        TextButton(onClick = { if (isRecording) stopVoiceRecording() else audioPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text(if (isRecording) "Stop recording" else "Record")
                        }
                    }
                    if (isRecording) Text("Recording… tap Stop recording to attach the audio.", color = Accent, fontSize = 12.sp)
                    voiceError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Attachments", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = Ink)
                        TextButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) { Text("+ Add file") }
                    }
                    attachments.forEach { attachment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(attachment.displayName, Modifier.weight(1f), maxLines = 1, color = Ink)
                            TextButton(onClick = {
                                try {
                                    val uri = AttachmentStorage.contentUri(context, attachment.uri)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                } catch (_: Exception) { attachmentError = "This attachment is missing or cannot be opened." }
                            }) { Text("Open") }
                            TextButton(onClick = { AttachmentStorage.delete(context, attachment.uri); onDeleteAttachment(attachment.id) }) { Text("Remove") }
                        }
                    }
                    attachmentError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reminder", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = Ink)
                        TextButton(onClick = { chooseReminder() }) { Text(if (reminderAt == null) "Set" else DateFormat.getDateTimeInstance().format(Date(reminderAt!!))) }
                        if (reminderAt != null) TextButton(onClick = {
                            reminderAt = null
                            scope.launch { onSave(draftId, title.ifBlank { "Untitled" }, body, tags, null, selectedColor) }
                        }) { Text("Clear") }
                    }
                    if (permissionDenied) Text("Notification permission is needed to show reminders.", color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Tags (comma separated)") }, singleLine = true)
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
