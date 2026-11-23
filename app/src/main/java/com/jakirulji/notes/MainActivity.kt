package com.jakirulji.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private val Ink = Color(0xFF20211F)
private val Paper = Color(0xFFF7F6F1)
private val Accent = Color(0xFF667B63)

data class Note(val id: Int, val title: String, val body: String, val color: Color, val pinned: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NotesTheme { NotesApp() } }
    }
}

@Composable
private fun NotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Accent, onPrimary = Color.White, background = Paper,
        surface = Color.White, onSurface = Ink, secondary = Color(0xFFD7B98E)
    ), content = content)
}

@Composable
private fun NotesApp() {
    var notes by remember { mutableStateOf(listOf(
        Note(1, "A little space to think", "Collect ideas, plans and the small things worth remembering.", Color(0xFFE8E8D8), true),
        Note(2, "Today", "• Review priorities\n• Take a quiet walk\n• Make time to read", Color(0xFFF3E6D6)),
        Note(3, "Ideas", "A place for unfinished thoughts and new beginnings.", Color(0xFFE4EAF0))
    )) }
    var query by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    val visible = notes.filter { (it.title + it.body).contains(query, true) }.sortedByDescending { it.pinned }

    Scaffold(containerColor = Paper, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { editing = null; editorOpen = true }, containerColor = Ink, contentColor = Color.White, shape = RoundedCornerShape(18.dp)) {
            Text("+", fontSize = 22.sp); Spacer(Modifier.width(8.dp)); Text("New note")
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR SPACE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Notes", fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Box(Modifier.size(42.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("✦", color = Accent, fontSize = 20.sp) }
            }
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search your thoughts") }, leadingIcon = { Text("⌕", fontSize = 24.sp) }, shape = RoundedCornerShape(18.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Color.Transparent, focusedBorderColor = Accent))
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent notes", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.weight(1f)); Text("${visible.size} NOTES", color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(visible, key = { it.id }) { note ->
                    NoteCard(note) { editing = note; editorOpen = true }
                }
            }
        }
    }
    if (editorOpen) NoteEditor(editing, onClose = { editorOpen = false }, onSave = { title, body ->
        val old = editing
        notes = if (old == null) notes + Note((notes.maxOfOrNull { it.id } ?: 0) + 1, title, body, Color(0xFFE8E8D8))
        else notes.map { if (it.id == old.id) it.copy(title = title, body = body) else it }
        editorOpen = false
    })
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Surface(onClick = onClick, color = note.color, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (note.pinned) Text("PIN", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(note.body, color = Ink.copy(alpha = .72f), fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Text("OPEN NOTE  ↗", color = Ink.copy(alpha = .55f), fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoteEditor(note: Note?, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(note) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note) { mutableStateOf(note?.body.orEmpty()) }
    AlertDialog(onDismissRequest = onClose, containerColor = Paper,
        title = { Text(if (note == null) "New note" else "Edit note", fontWeight = FontWeight.SemiBold) },
        text = { Column {
            OutlinedTextField(title, { title = it }, placeholder = { Text("Title") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(body, { body = it }, placeholder = { Text("Start writing…") }, minLines = 5)
        } },
        confirmButton = { TextButton(onClick = { onSave(title.ifBlank { "Untitled" }, body) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
