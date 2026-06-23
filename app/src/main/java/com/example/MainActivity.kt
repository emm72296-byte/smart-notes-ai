package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Folder
import com.example.data.Note
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ViewType {
    NAVIGATOR, EDITOR, AI_PANEL
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    WorkspaceScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun WorkspaceScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 900

    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()
    val selectedFolderId by viewModel.selectedFolderId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var activeViewMobile by remember { mutableStateOf(ViewType.NAVIGATOR) }
    var noteInputTitle by remember { mutableStateOf("") }
    var noteInputContent by remember { mutableStateOf("") }
    var noteInputTags by remember { mutableStateOf("") }

    // Synchronize note inputs on remote note change
    LaunchedEffect(selectedNote?.id) {
        selectedNote?.let {
            noteInputTitle = it.title
            noteInputContent = it.content
            noteInputTags = it.tags
        }
    }

    // Professional Auto-save debounce effect: 1.5 seconds of text silence
    LaunchedEffect(noteInputTitle, noteInputContent, noteInputTags) {
        val current = viewModel.selectedNote.value ?: return@LaunchedEffect
        if (noteInputTitle != current.title || noteInputContent != current.content || noteInputTags != current.tags) {
            delay(1500)
            if (noteInputTitle != current.title) {
                viewModel.updateNoteTitle(noteInputTitle)
            }
            if (noteInputContent != current.content) {
                viewModel.updateNoteContent(noteInputContent)
            }
            if (noteInputTags != current.tags) {
                viewModel.updateNoteTags(noteInputTags)
            }
        }
    }

    var selectedAiTool by remember { mutableStateOf<Int?>(null) } // null or 1..9

    // Layout Composition
    Row(modifier = modifier.fillMaxSize()) {
        if (isWideScreen) {
            // Navigator Sidebar (Folders & Notes)
            NavigatorPanel(
                folders = folders,
                notes = notes,
                selectedFolderId = selectedFolderId,
                selectedNoteId = selectedNote?.id,
                searchQuery = searchQuery,
                onSelectFolder = { viewModel.selectFolder(it) },
                onSelectNote = { viewModel.selectNote(it) },
                onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                onCreateFolder = { name, icon -> viewModel.createFolder(name, icon) },
                onCreateNote = { viewModel.createNewNote() },
                onDeleteFolder = { viewModel.deleteFolder(it) },
                onDeleteNote = { viewModel.deleteNote(it) },
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Central Editor Panel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (selectedNote != null) {
                    EditorPanel(
                        title = noteInputTitle,
                        content = noteInputContent,
                        tags = noteInputTags,
                        onTitleChanged = { noteInputTitle = it },
                        onContentChanged = { noteInputContent = it },
                        onTagsChanged = { noteInputTags = it },
                        onRunTool = { toolId ->
                            selectedAiTool = toolId
                            triggerAiTool(viewModel, toolId)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EmptyEditorState(onCreateNote = { viewModel.createNewNote() })
                }
            }

            // AI Action Workspace Visualization Panel on far right
            if (selectedAiTool != null && selectedNote != null) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AiToolWorkspacePanel(
                    viewModel = viewModel,
                    toolId = selectedAiTool!!,
                    onClose = { selectedAiTool = null },
                    modifier = Modifier
                        .width(480.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        } else {
            // Adaptive Compact / Mobile Layout: State View driven
            Box(modifier = Modifier.fillMaxSize()) {
                when (activeViewMobile) {
                    ViewType.NAVIGATOR -> {
                        NavigatorPanel(
                            folders = folders,
                            notes = notes,
                            selectedFolderId = selectedFolderId,
                            selectedNoteId = selectedNote?.id,
                            searchQuery = searchQuery,
                            onSelectFolder = { viewModel.selectFolder(it) },
                            onSelectNote = {
                                viewModel.selectNote(it)
                                if (it != null) {
                                    activeViewMobile = ViewType.EDITOR
                                }
                            },
                            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                            onCreateFolder = { name, icon -> viewModel.createFolder(name, icon) },
                            onCreateNote = {
                                viewModel.createNewNote()
                                activeViewMobile = ViewType.EDITOR
                            },
                            onDeleteFolder = { viewModel.deleteFolder(it) },
                            onDeleteNote = { viewModel.deleteNote(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ViewType.EDITOR -> {
                        if (selectedNote != null) {
                            EditorPanel(
                                title = noteInputTitle,
                                content = noteInputContent,
                                tags = noteInputTags,
                                onTitleChanged = { noteInputTitle = it },
                                onContentChanged = { noteInputContent = it },
                                onTagsChanged = { noteInputTags = it },
                                onRunTool = { toolId ->
                                    selectedAiTool = toolId
                                    triggerAiTool(viewModel, toolId)
                                    activeViewMobile = ViewType.AI_PANEL
                                },
                                modifier = Modifier.fillMaxSize(),
                                showBackButton = true,
                                onBackPress = { activeViewMobile = ViewType.NAVIGATOR }
                            )
                        } else {
                            activeViewMobile = ViewType.NAVIGATOR
                        }
                    }
                    ViewType.AI_PANEL -> {
                        if (selectedNote != null && selectedAiTool != null) {
                            AiToolWorkspacePanel(
                                viewModel = viewModel,
                                toolId = selectedAiTool!!,
                                onClose = {
                                    selectedAiTool = null
                                    activeViewMobile = ViewType.EDITOR
                                },
                                modifier = Modifier.fillMaxSize(),
                                showBackButton = true,
                                onBackPress = { activeViewMobile = ViewType.EDITOR }
                            )
                        } else {
                            activeViewMobile = ViewType.EDITOR
                        }
                    }
                }

                // Compact view Bottom navigation bar to swap views quickly!
                if (selectedNote != null && activeViewMobile != ViewType.NAVIGATOR) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                RoundedCornerShape(24.dp)
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                            .shadow(8.dp, RoundedCornerShape(24.dp))
                    ) {
                        TabRowMobile(
                            activeView = activeViewMobile,
                            selectedAiTool = selectedAiTool,
                            onTabSelected = { tab ->
                                activeViewMobile = tab
                                if (tab == ViewType.AI_PANEL && selectedAiTool == null) {
                                    selectedAiTool = 1 // default to Summary
                                    triggerAiTool(viewModel, 1)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

fun triggerAiTool(viewModel: MainViewModel, toolId: Int) {
    when (toolId) {
        1 -> viewModel.improveText("Professional")
        2 -> viewModel.generateSummary()
        3 -> viewModel.organizeIdeas()
        4 -> viewModel.generateMindMap()
        5 -> viewModel.generateActionPlan()
        6 -> viewModel.extractTasks()
        7 -> viewModel.generateStudyMaterials()
        8 -> viewModel.analyzeDecision()
        // 9 is chat, is triggered manually on query submit
    }
}

@Composable
fun TabRowMobile(
    activeView: ViewType,
    selectedAiTool: Int?,
    onTabSelected: (ViewType) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Notes Tab
        val navActive = activeView == ViewType.NAVIGATOR
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable { onTabSelected(ViewType.NAVIGATOR) }
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .background(
                        if (navActive) MaterialTheme.colorScheme.secondary else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Notes list",
                    tint = if (navActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "Notes",
                fontSize = 11.sp,
                fontWeight = if (navActive) FontWeight.Bold else FontWeight.Medium,
                color = if (navActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Editor Tab (Tools selection)
        val editActive = activeView == ViewType.EDITOR
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable { onTabSelected(ViewType.EDITOR) }
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .background(
                        if (editActive) MaterialTheme.colorScheme.secondary else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editor",
                    tint = if (editActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "Tools",
                fontSize = 11.sp,
                fontWeight = if (editActive) FontWeight.Bold else FontWeight.Medium,
                color = if (editActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // AI Workspace Tab (Chat assistant etc.)
        val aiActive = activeView == ViewType.AI_PANEL
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable { onTabSelected(ViewType.AI_PANEL) }
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .background(
                        if (aiActive) MaterialTheme.colorScheme.secondary else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                BadgedBox(badge = {
                    if (selectedAiTool != null) {
                        Badge { Text(selectedAiTool.toString()) }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI workspace",
                        tint = if (aiActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "Chat",
                fontSize = 11.sp,
                fontWeight = if (aiActive) FontWeight.Bold else FontWeight.Medium,
                color = if (aiActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- NAVIGATOR PANEL COMPOSABLE ---
@Composable
fun NavigatorPanel(
    folders: List<Folder>,
    notes: List<Note>,
    selectedFolderId: Int?,
    selectedNoteId: Int?,
    searchQuery: String,
    onSelectFolder: (Int?) -> Unit,
    onSelectNote: (Note?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onCreateNote: () -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onDeleteNote: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderIcon by remember { mutableStateOf("Folder") }

    Column(modifier = modifier.padding(16.dp)) {
        // App Header Logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        ),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Smart Notes",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Smart Notes AI",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "AI WORKSPACE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Search Note Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Search title, tag, content...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("search_note_input"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true
        )

        // Folders Section list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOLDERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            IconButton(
                onClick = { showAddFolderDialog = true },
                modifier = Modifier
                    .size(24.dp)
                    .testTag("add_folder_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Folder", modifier = Modifier.size(16.dp))
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedFolderId == null,
                    onClick = { onSelectFolder(null) },
                    label = { Text("All", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
            items(folders) { folder ->
                FilterChip(
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelectFolder(folder.id) },
                    label = { Text(folder.name, fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        if (folders.size > 1) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete",
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { onDeleteFolder(folder) }
                            )
                        }
                    }
                )
            }
        }

        // Notes Divider
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NOTES (${notes.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            Button(
                onClick = onCreateNote,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("add_note_btn"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New", fontSize = 11.sp)
            }
        }

        // Notes list
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Book,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No notes found",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                items(notes) { note ->
                    val isSelected = selectedNoteId == note.id
                    NoteRowCard(
                        note = note,
                        isSelected = isSelected,
                        onSelect = { onSelectNote(note) },
                        onDelete = { onDeleteNote(note) }
                    )
                }
            }
        }
    }

    // Add Folder dialog
    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("New Folder", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Folder", "Person", "Work", "Book", "Lightbulb").forEach { icon ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (newFolderIcon == icon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { newFolderIcon = icon }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val vector = when (icon) {
                                    "Person" -> Icons.Default.Person
                                    "Work" -> Icons.Default.Work
                                    "Book" -> Icons.Default.Book
                                    "Lightbulb" -> Icons.Default.Lightbulb
                                    else -> Icons.Default.Folder
                                }
                                Icon(
                                    vector,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (newFolderIcon == icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreateFolder(newFolderName, newFolderIcon)
                        newFolderName = ""
                        showAddFolderDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteRowCard(
    note: Note,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isSelected) 2.dp else 0.dp, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content.ifBlank { "Empty note content..." },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (note.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    note.tags.split(",").forEach { tag ->
                        if (tag.trim().isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "#" + tag.trim(),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- CORE EDITOR PANEL ---
@Composable
fun EditorPanel(
    title: String,
    content: String,
    tags: String,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onTagsChanged: (String) -> Unit,
    onRunTool: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackPress: () -> Unit = {}
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Toolbar with close trigger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBackPress) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                text = "Note Workspace Editor",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.CloudDone, contentDescription = "Saved state", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Auto-saved", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }

        // Title textfield
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChanged,
            placeholder = { Text("Note Title", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("note_title_input"),
            textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        // Tags entry row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedTextField(
                value = tags,
                onValueChange = onTagsChanged,
                placeholder = { Text("tags separated by comma, e.g. project, research, idea", fontSize = 11.sp) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )
        }

        // Dedicated AI Core Action Toolbar Frame
        Text(
            text = "AI COGNITIVE TRANSCEND_ LAB",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Visual Toolbar Grid of 9 Tools
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            val tools = listOf(
                Pair(1, "Improve Writing"),
                Pair(2, "AI Summary"),
                Pair(3, "Idea Organizer"),
                Pair(4, "Mind Map"),
                Pair(5, "Action Plan"),
                Pair(6, "Task Boards"),
                Pair(7, "Study Center"),
                Pair(8, "Decision Analyzer"),
                Pair(9, "Interactive Chat")
            )
            items(tools) { (id, label) ->
                val icon = when (id) {
                    1 -> Icons.Default.CompareArrows
                    2 -> Icons.Default.Dashboard
                    3 -> Icons.Default.Category
                    4 -> Icons.Default.Share
                    5 -> Icons.Default.Timeline
                    6 -> Icons.Default.ListAlt
                    7 -> Icons.Default.School
                    8 -> Icons.Default.Assessment
                    else -> Icons.Default.AutoAwesome
                }
                Surface(
                    onClick = { onRunTool(id) },
                    modifier = Modifier.shadow(1.dp, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Large note body input area
        OutlinedTextField(
            value = content,
            onValueChange = onContentChanged,
            placeholder = { Text("Start typing your messy thoughts, details, plans here...", fontSize = 14.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("note_content_input"),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun EmptyEditorState(onCreateNote: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Welcome to Smart Notes AI Workspace",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Select a note or create a new one to unleash cognitive AI transformation tools.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onCreateNote) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create New Note")
            }
        }
    }
}


// --- AI WORKSPACE DISPLAY RENDERING PANELS (FAR RIGHT OR MOBILE OVERLAY) ---
@Composable
fun AiToolWorkspacePanel(
    viewModel: MainViewModel,
    toolId: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackPress: () -> Unit = {}
) {
    val note by viewModel.selectedNote.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        // Tool header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBackPress) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                }
            }
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (toolId) {
                    1 -> "AI Writing Enhancer"
                    2 -> "Intelligent Dashboard Summary"
                    3 -> "Cognitive Idea Organizer"
                    4 -> "Knowledge Graph Mind Map"
                    5 -> "Visual Execution Roadmap"
                    6 -> "Operational Kanban Extract"
                    7 -> "Learn Labs Study Center"
                    8 -> "Decision Analysis Matrix"
                    else -> "Interactive Workspace Chat"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Tool", modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Contents
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (note == null) {
                Text("Please select or write a note first.", modifier = Modifier.padding(16.dp))
            } else {
                when (toolId) {
                    1 -> TextImprovementView(viewModel = viewModel, note = note!!)
                    2 -> SummaryDashboardView(viewModel = viewModel)
                    3 -> IdeaTreeView(viewModel = viewModel)
                    4 -> InteractiveMindMapView(viewModel = viewModel)
                    5 -> RoadmapResultView(viewModel = viewModel)
                    6 -> TaskKanbanView(viewModel = viewModel)
                    7 -> StudyDashboardView(viewModel = viewModel)
                    8 -> DecisionMatrixView(viewModel = viewModel)
                    else -> ChatAssistantPanel(viewModel = viewModel)
                }
            }
        }
    }
}

// --- TOOL 1: TEXT IMPROVEMENT ---
@Composable
fun TextImprovementView(viewModel: MainViewModel, note: Note) {
    val uiState by viewModel.aiImprovedState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Controls Row
        Text("Writing improvement tone modes:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            val modes = listOf("Formal", "Professional", "Friendly", "Concise", "Persuasive")
            modes.forEach { mode ->
                Button(
                    onClick = { viewModel.improveText(mode) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(mode, fontSize = 10.sp)
                }
            }
        }

        when (val state = uiState) {
            AiUiState.Idle -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Select a mode above to polish note writing style.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("ai_tool_status_text"))
                }
            }
            AiUiState.Loading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("ai_tool_loading_indicator"))
                }
            }
            is AiUiState.Success -> {
                val data = state.data
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enhancements Rationale", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(data.changesReason, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        // DIFF Side-by-Side look
                        Column(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                            // Header
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                                Text("Comparison Diff View", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            // Original on top, improved on bottom for compact visual flow
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Original Draft", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(data.original, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("AI Refined Note", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(data.improved, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            is AiUiState.Error -> {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

// --- TOOL 2: AI SUMMARY ---
@Composable
fun SummaryDashboardView(viewModel: MainViewModel) {
    val uiState by viewModel.aiSummaryState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        AiUiState.Idle -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Executive summary pending. Trigger processing in editor.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_tool_status_text"))
            }
        }
        AiUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("ai_tool_loading_indicator"))
            }
        }
        is AiUiState.Success -> {
            val d = state.data
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Core summary card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Executive Summary", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(d.summary, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                item {
                    SummarySection(
                        title = "Key Takeaways",
                        items = d.keyPoints,
                        icon = Icons.Default.Star,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    SummarySection(
                        title = "Important Names & Roles",
                        items = d.importantNames,
                        icon = Icons.Default.Person,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }

                item {
                    SummarySection(
                        title = "Milestones & Dates",
                        items = d.importantDates,
                        icon = Icons.Default.Today,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                item {
                    SummarySection(
                        title = "Action items",
                        items = d.actionItems,
                        icon = Icons.Default.CheckCircle,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        is AiUiState.Error -> {
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun SummarySection(
    title: String,
    items: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (items.isEmpty()) {
                Text("None identified inside notes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            } else {
                items.forEach { dot ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", color = tint, modifier = Modifier.padding(end = 6.dp))
                        Text(dot, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}


// --- TOOL 3: IDEA ORGANIZER ---
@Composable
fun IdeaTreeView(viewModel: MainViewModel) {
    val uiState by viewModel.aiIdeaState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        AiUiState.Idle -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Structure trees pending processing.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_tool_status_text"))
            }
        }
        AiUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("ai_tool_loading_indicator"))
            }
        }
        is AiUiState.Success -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                item {
                    Text("Interactive Brain Dump Tree Structure:", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                }
                item {
                    TreeNodeCard(node = state.data, level = 0)
                }
            }
        }
        is AiUiState.Error -> {
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun TreeNodeCard(node: IdeaNode, level: Int) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable { expanded = !expanded }
                .background(
                    if (level == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.children.isEmpty()) Icons.Default.SubdirectoryArrowRight else if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (level == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = node.title,
                fontSize = if (level == 0) 14.sp else 12.sp,
                fontWeight = if (level == 0) FontWeight.Bold else FontWeight.Medium,
                color = if (level == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        if (expanded && node.children.isNotEmpty()) {
            node.children.forEach { child ->
                TreeNodeCard(node = child, level = level + 1)
            }
        }
    }
}


// --- TOOL 4: MIND MAP GENERATOR ---
@Composable
fun InteractiveMindMapView(viewModel: MainViewModel) {
    val uiState by viewModel.aiMindMapState.collectAsStateWithLifecycle()

    var zoomScale by remember { mutableStateOf(1f) }
    // Interactive graph nodes dragging state mapping: ID -> DynamicOffset
    val nodeOffsets = remember { mutableStateMapOf<String, Offset>() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Drag nodes around to rearrange hierarchy!", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zoom: ", fontSize = 10.sp)
                Slider(
                    value = zoomScale,
                    onValueChange = { zoomScale = it },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.width(100.dp)
                )
            }
        }

        when (val state = uiState) {
            AiUiState.Idle -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Interactive Mind Map pending generation.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_tool_status_text"))
                }
            }
            AiUiState.Loading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("ai_tool_loading_indicator"))
                }
            }
            is AiUiState.Success -> {
                val data = state.data
                // Seed initial offsets only if empty
                LaunchedEffect(data.nodes) {
                    if (nodeOffsets.isEmpty()) {
                        data.nodes.forEachIndexed { index, node ->
                            val defaultX = when (node.category) {
                                "central" -> 200f
                                "parent" -> if (index % 2 == 0) 100f else 300f
                                else -> if (index % 2 == 0) 60f else 340f
                            }
                            val defaultY = 100f + (index * 80f)
                            nodeOffsets[node.id] = Offset(defaultX, defaultY)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    // Draw relationship lines onto Background Canvas!
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        data.edges.forEach { edge ->
                            val fromOffset = nodeOffsets[edge.from]
                            val toOffset = nodeOffsets[edge.to]
                            if (fromOffset != null && toOffset != null) {
                                drawLine(
                                    color = Color.LightGray,
                                    start = fromOffset * zoomScale,
                                    end = toOffset * zoomScale,
                                    strokeWidth = 3f * zoomScale
                                )
                            }
                        }
                    }

                    // Render Draggable nodes overlay!
                    data.nodes.forEach { node ->
                        val nodeOffset = nodeOffsets[node.id] ?: Offset(150f, 150f)
                        val nodeColor = try { Color(android.graphics.Color.parseColor(node.color)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = (nodeOffset.x * zoomScale).dp / 2.5f,
                                    y = (nodeOffset.y * zoomScale).dp / 2.5f
                                )
                                .pointerInput(node.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val cur = nodeOffsets[node.id] ?: Offset(150f, 150f)
                                        nodeOffsets[node.id] = cur + dragAmount
                                    }
                                }
                                .background(nodeColor, RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = node.label,
                                fontSize = (11f * zoomScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            is AiUiState.Error -> {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}


// --- TOOL 5: ACTION PLAN GENERATOR ---
@Composable
fun RoadmapResultView(viewModel: MainViewModel) {
    val uiState by viewModel.aiActionPlanState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        AiUiState.Idle -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Execution roadmap pending generation.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_tool_status_text"))
            }
        }
        AiUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("ai_tool_loading_indicator"))
            }
        }
        is AiUiState.Success -> {
            val phases = state.data
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Visual Execution Roadmap timeline", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
                items(phases) { phase ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Phase badge header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(phase.phase, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(phase.duration, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Key Objectives:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            phase.objectives.forEach { obj ->
                                Row(modifier = Modifier.padding(start = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(obj, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Deliverables:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            phase.deliverables.forEach { del ->
                                Row(modifier = Modifier.padding(start = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(del, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        is AiUiState.Error -> {
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}


// --- TOOL 6: TASK EXTRACTION KANBAN ---
@Composable
fun TaskKanbanView(viewModel: MainViewModel) {
    val uiState by viewModel.aiTasksState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        AiUiState.Idle -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Operational Kanban lists pending translation.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_tool_status_text"))
            }
        }
        AiUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("ai_tool_loading_indicator"))
            }
        }
        is AiUiState.Success -> {
            val tasks = state.data
            // Split tasks into 3 columns
            val todo = tasks.filter { it.status == "To Do" }
            val inProgress = tasks.filter { it.status == "In Progress" }
            val done = tasks.filter { it.status == "Done" }

            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text("Board View (Use buttons to drag/move columns)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { KanbanColumn("To Do", todo, viewModel) }
                    item { KanbanColumn("In Progress", inProgress, viewModel) }
                    item { KanbanColumn("Done", done, viewModel) }
                }
            }
        }
        is AiUiState.Error -> {
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun KanbanColumn(
    title: String,
    tasks: List<KanbanTask>,
    viewModel: MainViewModel
) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(tasks.size.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tasks) { task ->
                    KanbanTaskCard(task = task, onStatusChange = { new -> viewModel.updateTaskStatus(task.id, new) })
                }
            }
        }
    }
}

@Composable
fun KanbanTaskCard(task: KanbanTask, onStatusChange: (String) -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kanban_task_${task.title.replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(task.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 3)
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Priority Badge
                Box(
                    modifier = Modifier
                        .background(
                            when (task.priority) {
                                "High" -> Color(0xFFFEE2E2)
                                "Medium" -> Color(0xFFFEF3C7)
                                else -> Color(0xFFD1FAE5)
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        task.priority,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (task.priority) {
                            "High" -> Color(0xFFEF4444)
                            "Medium" -> Color(0xFFD97706)
                            else -> Color(0xFF10B981)
                        }
                    )
                }

                // Time Badge
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(task.estimatedTime, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Text(task.dueDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Interactive Move Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (task.status != "To Do") {
                    IconButton(
                        onClick = {
                            val target = if (task.status == "Done") "In Progress" else "To Do"
                            onStatusChange(target)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(14.dp))
                    }
                }
                if (task.status != "Done") {
                    IconButton(
                        onClick = {
                            val target = if (task.status == "To Do") "In Progress" else "Done"
                            onStatusChange(target)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}


// --- TOOL 7: STUDY LABS ---
@Composable
fun StudyDashboardView(viewModel: MainViewModel) {
    val uiState by viewModel.aiStudyState.collectAsStateWithLifecycle()

    var activeTabIdx by remember { mutableStateOf(0) }

    when (val state = uiState) {
        AiUiState.Idle -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Study Center pending generation.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_tool_status_text"))
            }
        }
        AiUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("ai_tool_loading_indicator"))
            }
        }
        is AiUiState.Success -> {
            val studyResult = state.data

            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = activeTabIdx) {
                    Tab(selected = activeTabIdx == 0, onClick = { activeTabIdx = 0 }, text = { Text("Summary", fontSize = 11.sp) })
                    Tab(selected = activeTabIdx == 1, onClick = { activeTabIdx = 1 }, text = { Text("Cards", fontSize = 11.sp) })
                    Tab(selected = activeTabIdx == 2, onClick = { activeTabIdx = 2 }, text = { Text("Quiz", fontSize = 11.sp) })
                    Tab(selected = activeTabIdx == 3, onClick = { activeTabIdx = 3 }, text = { Text("Concepts", fontSize = 11.sp) })
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when (activeTabIdx) {
                        0 -> StudySummaryTab(studyResult.summary)
                        1 -> FlashcardAnimationGrid(studyResult.flashcards)
                        2 -> StudyQuizInterface(studyResult.quiz)
                        else -> StudyConceptsTab(studyResult.concepts)
                    }
                }
            }
        }
        is AiUiState.Error -> {
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun StudySummaryTab(summary: String) {
    Card(modifier = Modifier.fillMaxSize(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Module Learning Synthesis:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(summary, fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun FlashcardAnimationGrid(cards: List<Flashcard>) {
    var curCardIdx by remember { mutableStateOf(0) }
    var rotated by remember { mutableStateOf(false) }

    // Spring flip rotation mechanics custom animator
    val rotation by animateFloatAsState(
        targetValue = if (rotated) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tap Card to Flip & Reveal Term Definition", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        if (cards.isEmpty()) {
            Text("No flashcards found")
        } else {
            val card = cards[curCardIdx]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clickable { rotated = !rotated },
                colors = CardDefaults.cardColors(
                    containerColor = if (rotated) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotation < 90f) {
                        Text(
                            text = card.front,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            text = card.back,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.graphicsLayer { rotationY = 180f }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        rotated = false
                        curCardIdx = if (curCardIdx > 0) curCardIdx - 1 else cards.size - 1
                    },
                    modifier = Modifier.testTag("prev_card_btn")
                ) {
                    Text("Prev")
                }
                Text("${curCardIdx + 1} / ${cards.size}", fontSize = 12.sp)
                Button(
                    onClick = {
                        rotated = false
                        curCardIdx = if (curCardIdx < cards.size - 1) curCardIdx + 1 else 0
                    },
                    modifier = Modifier.testTag("next_card_btn")
                ) {
                    Text("Next")
                }
            }
        }
    }
}

@Composable
fun StudyQuizInterface(quiz: List<QuizQuestion>) {
    var curQIdx by remember { mutableStateOf(0) }
    var selectedAnsIdx by remember { mutableStateOf<Int?>(null) }
    var answered by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (quiz.isEmpty()) {
            Text("No Quiz compiled.")
        } else if (curQIdx >= quiz.size) {
            // Results Card
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Session Completed!", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your Score: $score / ${quiz.size}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        curQIdx = 0
                        selectedAnsIdx = null
                        answered = false
                        score = 0
                    }) {
                        Text("Retry Quiz")
                    }
                }
            }
        } else {
            val q = quiz[curQIdx]
            Text("Quiz Progress: ${curQIdx + 1} / ${quiz.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Text(q.question, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            q.options.forEachIndexed { idx, op ->
                val isSelected = selectedAnsIdx == idx
                val optionBg = when {
                    answered && idx == q.correctIndex -> Color(0xFFD1FAE5) // light emerald
                    answered && isSelected && idx != q.correctIndex -> Color(0xFFFEE2E2) // light red
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                val labelColor = when {
                    answered && idx == q.correctIndex -> Color(0xFF047857)
                    answered && isSelected && idx != q.correctIndex -> Color(0xFFB91C1C)
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(optionBg)
                        .clickable { if (!answered) selectedAnsIdx = idx }
                        .padding(12.dp)
                ) {
                    Text(op, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = labelColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!answered) {
                    Button(
                        onClick = {
                            if (selectedAnsIdx != null) {
                                answered = true
                                if (selectedAnsIdx == q.correctIndex) {
                                    score += 1
                                }
                            }
                        },
                        enabled = selectedAnsIdx != null,
                        modifier = Modifier.testTag("submit_quiz_answer")
                    ) {
                        Text("Submit")
                    }
                } else {
                    Button(
                        onClick = {
                            curQIdx += 1
                            selectedAnsIdx = null
                            answered = false
                        },
                        modifier = Modifier.testTag("next_quiz_q")
                    ) {
                        Text("Next Question")
                    }
                }
            }
        }
    }
}

@Composable
fun StudyConceptsTab(concepts: List<KeyConcept>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(concepts) { c ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(c.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}


// --- TOOL 8: DECISION MATRIX ANALYZER ---
@Composable
fun DecisionMatrixView(viewModel: MainViewModel) {
    val uiState by viewModel.aiDecisionState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        AiUiState.Idle -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Decision recommendation columns pending assessment.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_tool_status_text"))
            }
        }
        AiUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("ai_tool_loading_indicator"))
            }
        }
        is AiUiState.Success -> {
            val options = state.data
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Interactive Options Scorecard Matrix:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                items(options) { opt ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Score heading
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(opt.option, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Score: ${opt.score}/10", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Side-by-side Pros/Cons columns layout
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Pros", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF057857))
                                    opt.pros.forEach { pro ->
                                        Text("• " + pro, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Cons", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                    opt.cons.forEach { con ->
                                        Text("• " + con, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

                            if (opt.risks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Key Risks:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                opt.risks.forEach { r ->
                                    Text("⚠️ " + r, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Strategic Recommendation:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(opt.recommendation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        is AiUiState.Error -> {
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}


// --- TOOL 9: INTERACTIVE CHAT PANEL ---
@Composable
fun ChatAssistantPanel(viewModel: MainViewModel) {
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.aiChatLoading.collectAsStateWithLifecycle()
    var inputQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Text(
                "Chat acts scoped to your active note automatically, reading citations as references.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        // Chat text dialogue list window
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatHistory) { msg ->
                val isAi = msg.sender == "ai"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isAi) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                shape = if (isAi) RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(topStart = 14.dp, topEnd = 0.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                            )
                            .padding(12.dp)
                            .widthIn(max = 300.dp)
                    ) {
                        Column {
                            Text(
                                text = msg.text,
                                fontSize = 12.sp,
                                color = if (isAi) MaterialTheme.colorScheme.onSurface else Color.White
                            )
                            if (msg.citations.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Citation: " + msg.citations.joinToString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAi) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
            if (isLoading) {
                item {
                    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp).testTag("chat_loading_indicator"))
                    }
                }
            }
        }

        // Input send field bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                placeholder = { Text("Ask anything about note...", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_query_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputQuery.isNotBlank()) {
                        viewModel.sendChatMessage(inputQuery)
                        inputQuery = ""
                    }
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .testTag("chat_send_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
