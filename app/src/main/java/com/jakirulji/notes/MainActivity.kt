package com.jakirulji.notes

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jakirulji.notes.data.NoteEntity
import com.jakirulji.notes.data.FolderRepository
import com.jakirulji.notes.data.NoteRepository
import com.jakirulji.notes.data.NotesDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Calendar
import java.text.DateFormat
import java.util.Date
import com.jakirulji.notes.notifications.ReminderScheduler

private val Ink = Color(0xFF20211F)
private val Paper = Color(0xFFF7F6F1)
private val Accent = Color(0xFF667B63)
private const val DEFAULT_NOTE_COLOR = 0xFFE8E8D8L

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
        colorScheme = lightColorScheme(
            primary = Accent, onPrimary = Color.White, background = Paper,
            surface = Color.White, onSurface = Ink, secondary = Color(0xFFD7B98E)
        ),
        content = content
    )
}

@Composable
private fun NotesApp(repository: NoteRepository, folderRepository: FolderRepository, reminderScheduler: ReminderScheduler) {
    val notes by repository.activeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val trash by repository.trashedNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val folders by folderRepository.folders().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showTrash by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var createFolderOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val visible = (if (showTrash) trash else notes).filter {
        (it.title + " " + it.content + " " + it.tags).contains(query, ignoreCase = true) &&
            (showTrash || selectedFolderId == null || it.folderId == selectedFolderId)
    }

    Scaffold(
        containerColor = Paper,
        floatingActionButton = {
            if (!showTrash) {
                ExtendedFloatingActionButton(
                    onClick = { editing = null; editorOpen = true },
                    containerColor = Ink, contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("+", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("New note")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (showTrash) "YOUR ARCHIVE" else "YOUR SPACE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(if (showTrash) "Trash" else "Notes", fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Box(Modifier.size(42.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✦", color = Accent, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showTrash, onClick = { showTrash = false; query = "" }, label = { Text("Notes") })
                FilterChip(selected = showTrash, onClick = { showTrash = true; query = "" }, label = { Text("Trash") })
                Spacer(Modifier.weight(1f))
                if (showTrash && trash.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { repository.emptyTrash() } }) { Text("Empty") }
                }
            }
            if (!showTrash) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(selected = selectedFolderId == null, onClick = { selectedFolderId = null }, label = { Text("All folders") })
                    folders.forEach { folder ->
                        FilterChip(selected = selectedFolderId == folder.id, onClick = { selectedFolderId = folder.id }, label = { Text(folder.name) })
                    }
                    AssistChip(onClick = { newFolderName = ""; createFolderOpen = true }, label = { Text("+ Folder") })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your thoughts") }, leadingIcon = { Text("⌕", fontSize = 24.sp) },
                shape = RoundedCornerShape(18.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = Accent
                )
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (showTrash) "Recently deleted" else "Recent notes", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("${visible.size} ITEMS", color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (showTrash) "Your trash is empty" else "A clear space for your next thought.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(visible, key = { it.id }) { note ->
                        NoteCard(
                            note = note, inTrash = showTrash, folders = folders,
                            onOpen = { if (!showTrash) { editing = note; editorOpen = true } },
                            onMoveFolder = { folderId -> scope.launch { folderRepository.moveNote(note.id, folderId) } },
                            onPin = { scope.launch { repository.togglePin(note) } },
                            onTrash = { scope.launch { repository.trash(note.id) } },
                            onRestore = { scope.launch { repository.restore(note.id) } },
                            onDeleteForever = { scope.launch { repository.deleteForever(note.id) } }
                        )
                    }
                }
            }
        }
    }

    if (createFolderOpen) {
        AlertDialog(
            onDismissRequest = { createFolderOpen = false },
            title = { Text("Create folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    placeholder = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newFolderName.trim()
                    if (name.isNotEmpty()) scope.launch { folderRepository.create(name) }
                    createFolderOpen = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createFolderOpen = false }) { Text("Cancel") } }
        )
    }

    if (editorOpen) {
        NoteEditor(
            note = editing,
            onClose = { editorOpen = false },
            onSave = { id, title, body, tags, reminderAt ->
                scope.launch {
                    repository.save(id, title, body, editing?.color ?: DEFAULT_NOTE_COLOR, tags = tags, folderId = editing?.folderId ?: selectedFolderId)
                    repository.setReminder(id, reminderAt)
                    reminderScheduler.schedule(id, title.ifBlank { "Untitled" }, reminderAt)
                }
            },
            onAttach = { id, title, body, tags, uri, name, mimeType, sizeBytes ->
                scope.launch {
                    repository.save(id, title, body, editing?.color ?: DEFAULT_NOTE_COLOR, tags = tags, folderId = editing?.folderId ?: selectedFolderId)
                    repository.addAttachment(id, uri, name, mimeType, sizeBytes)
                }
            },
            attachmentsFlow = { id -> repository.attachments(id) },
            onDeleteAttachment = { attachmentId -> scope.launch { repository.deleteAttachment(attachmentId) } }
        )
    }
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
                Text(note.title, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (note.pinned && !inTrash) Text("PIN", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(note.content, color = Ink.copy(alpha = .72f), fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4)
            }
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
                            folders.forEach { folder ->
                                DropdownMenuItem(text = { Text(folder.name) }, onClick = { onMoveFolder(folder.id); folderMenuOpen = false })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onTrash) { Text("Move to trash") }
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(
    note: NoteEntity?,
    onClose: () -> Unit,
    onSave: (String, String, String, String, Long?) -> Unit,
    onAttach: (String, String, String, String, String, String, String, Long) -> Unit,
    attachmentsFlow: (String) -> kotlinx.coroutines.flow.Flow<List<com.jakirulji.notes.data.AttachmentEntity>>,
    onDeleteAttachment: (String) -> Unit
) {
    val context = LocalContext.current
    val draftId = rememberSaveable(note?.id) { note?.id ?: UUID.randomUUID().toString() }
    var title by rememberSaveable(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by rememberSaveable(note?.id) { mutableStateOf(note?.content.orEmpty()) }
    var tags by rememberSaveable(note?.id) { mutableStateOf(note?.tags.orEmpty()) }
    var reminderAt by rememberSaveable(note?.id) { mutableStateOf(note?.reminderAt) }
    var permissionDenied by remember { mutableStateOf(false) }
    val attachments by attachmentsFlow(draftId).collectAsStateWithLifecycle(initialValue = emptyList())

    fun persistDraft() {
        if (title.isNotBlank() || body.isNotBlank() || tags.isNotBlank()) {
            onSave(
                draftId,
                title.ifBlank { "Untitled" },
                body,
                tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", "),
                reminderAt
            )
        }
    }

    fun attachUri(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers do not expose persistable grants; the attachment remains best-effort.
        }
        var displayName = uri.lastPathSegment ?: "Attachment"
        var sizeBytes = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
            }
        }
        onAttach(
            draftId, title.ifBlank { "Untitled" }, body,
            tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", "),
            uri.toString(), displayName, context.contentResolver.getType(uri) ?: "application/octet-stream", sizeBytes
        )
    }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) attachUri(uri)
    }
    var pendingReminder by remember { mutableStateOf<Long?>(null) }
    fun applyReminder(timestamp: Long) {
        reminderAt = timestamp
        onSave(draftId, title.ifBlank { "Untitled" }, body, tags, timestamp)
        pendingReminder = null
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingReminder?.let(::applyReminder) else {
            permissionDenied = true
            pendingReminder = null
        }
    }
    fun chooseReminder() {
        val now = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, day ->
            val selected = Calendar.getInstance().apply { set(year, month, day) }
            TimePickerDialog(context, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0)
                selected.set(Calendar.MILLISECOND, 0)
                if (selected.timeInMillis > System.currentTimeMillis()) {
                    val timestamp = selected.timeInMillis
                    if (Build.VERSION.SDK_INT >= 33) {
                        pendingReminder = timestamp
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else applyReminder(timestamp)
                }
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    LaunchedEffect(draftId, title, body, tags, reminderAt) {
        if (title.isNotBlank() || body.isNotBlank() || tags.isNotBlank()) {
            delay(700)
            persistDraft()
        }
    }

    AlertDialog(
        onDismissRequest = { persistDraft(); onClose() },
        containerColor = Paper,
        title = { Text(if (note == null) "New note" else "Edit note", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Title") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Start writing…") },
                    minLines = 5
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Attachments", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) { Text("+ Add file") }
                }
                attachments.forEach { attachment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(attachment.displayName, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = {
                            val uri = Uri.parse(attachment.uri)
                            val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try { context.startActivity(viewIntent) } catch (_: android.content.ActivityNotFoundException) { }
                        }) { Text("Open") }
                        TextButton(onClick = { onDeleteAttachment(attachment.id) }) { Text("Remove") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reminder", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { chooseReminder() }) { Text(if (reminderAt == null) "Set" else DateFormat.getDateTimeInstance().format(Date(reminderAt!!))) }
                    if (reminderAt != null) TextButton(onClick = { reminderAt = null; onSave(draftId, title.ifBlank { "Untitled" }, body, tags, null) }) { Text("Clear") }
                }
                if (permissionDenied) Text("Notification permission is needed to show reminders.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tags (comma separated)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { persistDraft(); onClose() }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { persistDraft(); onClose() }) { Text("Close") }
        }
    )
}
