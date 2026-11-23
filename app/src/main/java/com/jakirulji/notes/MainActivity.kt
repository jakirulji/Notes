package com.jakirulji.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.launch

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
        setContent { NotesTheme { NotesApp(repository, folderRepository) } }
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
private fun NotesApp(repository: NoteRepository, folderRepository: FolderRepository) {
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
                            note = note, inTrash = showTrash,
                            onOpen = { if (!showTrash) { editing = note; editorOpen = true } },
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
            onSave = { title, body, tags ->
                scope.launch {
                    repository.save(editing?.id, title, body, editing?.color ?: DEFAULT_NOTE_COLOR, tags = tags, folderId = editing?.folderId ?: selectedFolderId)
                    editorOpen = false
                }
            }
        )
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity, inTrash: Boolean, onOpen: () -> Unit, onPin: () -> Unit,
    onTrash: () -> Unit, onRestore: () -> Unit, onDeleteForever: () -> Unit
) {
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
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onTrash) { Text("Move to trash") }
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(note: NoteEntity?, onClose: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note?.id) { mutableStateOf(note?.content.orEmpty()) }
    var tags by remember(note?.id) { mutableStateOf(note?.tags.orEmpty()) }
    AlertDialog(
        onDismissRequest = onClose, containerColor = Paper,
        title = { Text(if (note == null) "New note" else "Edit note", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("Title") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Start writing…") }, minLines = 5)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = tags, onValueChange = { tags = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Tags (comma separated)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.ifBlank { "Untitled" }, body, tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
