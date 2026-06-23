package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.Folder
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.data.SmartNotesDatabase
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.GenerationConfig
import com.example.network.Part
import com.example.network.RetrofitClient
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AiUiState<out T> {
    object Idle : AiUiState<Nothing>
    object Loading : AiUiState<Nothing>
    data class Success<T>(val data: T) : AiUiState<T>
    data class Error(val message: String) : AiUiState<Nothing>
}

data class ChatMessage(
    val sender: String, // "user" or "ai"
    val text: String,
    val citations: List<String> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    val folders: StateFlow<List<Folder>>
    
    // Search & Selected folder query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<Int?>(null)
    val selectedFolderId = _selectedFolderId.asStateFlow()

    // Notes mapped by search & folders
    val notes: StateFlow<List<Note>>

    // Current selected note
    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote = _selectedNote.asStateFlow()

    // AI Tool Output States
    private val _aiSummaryState = MutableStateFlow<AiUiState<SummaryResult>>(AiUiState.Idle)
    val aiSummaryState = _aiSummaryState.asStateFlow()

    private val _aiTasksState = MutableStateFlow<AiUiState<List<KanbanTask>>>(AiUiState.Idle)
    val aiTasksState = _aiTasksState.asStateFlow()

    private val _aiActionPlanState = MutableStateFlow<AiUiState<List<ActionPhase>>>(AiUiState.Idle)
    val aiActionPlanState = _aiActionPlanState.asStateFlow()

    private val _aiMindMapState = MutableStateFlow<AiUiState<MindMapResult>>(AiUiState.Idle)
    val aiMindMapState = _aiMindMapState.asStateFlow()

    private val _aiIdeaState = MutableStateFlow<AiUiState<IdeaNode>>(AiUiState.Idle)
    val aiIdeaState = _aiIdeaState.asStateFlow()

    private val _aiStudyState = MutableStateFlow<AiUiState<StudyResult>>(AiUiState.Idle)
    val aiStudyState = _aiStudyState.asStateFlow()

    private val _aiDecisionState = MutableStateFlow<AiUiState<List<DecisionOption>>>(AiUiState.Idle)
    val aiDecisionState = _aiDecisionState.asStateFlow()

    private val _aiImprovedState = MutableStateFlow<AiUiState<TextImprovementResult>>(AiUiState.Idle)
    val aiImprovedState = _aiImprovedState.asStateFlow()

    // Chat History
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory = _chatHistory.asStateFlow()
    private val _aiChatLoading = MutableStateFlow(false)
    val aiChatLoading = _aiChatLoading.asStateFlow()

    init {
        val database = SmartNotesDatabase.getDatabase(application)
        repository = NoteRepository(database.noteDao())
        folders = repository.allFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Combine filter logic: Folder selection + Search query
        notes = combine(repository.allNotes, _selectedFolderId, _searchQuery) { allNotes, folderId, query ->
            var filtered = allNotes
            if (folderId != null) {
                filtered = filtered.filter { it.folderId == folderId }
            }
            if (query.isNotEmpty()) {
                filtered = filtered.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true) ||
                    it.tags.contains(query, ignoreCase = true)
                }
            }
            filtered
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Seed initial folders if empty
        viewModelScope.launch {
            folders.collect { list ->
                if (list.isEmpty()) {
                    repository.insertFolder(Folder(name = "Personal", iconName = "Person"))
                    repository.insertFolder(Folder(name = "Work", iconName = "Work"))
                    repository.insertFolder(Folder(name = "Study", iconName = "Book"))
                    repository.insertFolder(Folder(name = "Brainstorm", iconName = "Lightbulb"))
                }
            }
        }
    }

    fun selectFolder(folderId: Int?) {
        _selectedFolderId.value = folderId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectNote(note: Note?) {
        _selectedNote.value = note
        _chatHistory.value = emptyList() // clear chat on note shift
        if (note != null) {
            // Load caches immediately from selected note
            _aiSummaryState.value = if (note.aiSummaryJson != null) AiUiState.Success(SummaryResult.fromJson(note.aiSummaryJson)) else AiUiState.Idle
            _aiTasksState.value = if (note.aiTasksJson != null) AiUiState.Success(KanbanTask.fromJsonList(note.aiTasksJson)) else AiUiState.Idle
            _aiActionPlanState.value = if (note.aiActionPlanJson != null) AiUiState.Success(ActionPhase.fromJsonList(note.aiActionPlanJson)) else AiUiState.Idle
            _aiMindMapState.value = if (note.aiMindMapJson != null) AiUiState.Success(MindMapResult.fromJson(note.aiMindMapJson)) else AiUiState.Idle
            _aiIdeaState.value = if (note.aiIdeaJson != null) AiUiState.Success(IdeaNode.fromJson(note.aiIdeaJson)) else AiUiState.Idle
            _aiStudyState.value = if (note.aiStudyJson != null) AiUiState.Success(StudyResult.fromJson(note.aiStudyJson)) else AiUiState.Idle
            _aiDecisionState.value = if (note.aiDecisionJson != null) AiUiState.Success(DecisionOption.fromJsonList(note.aiDecisionJson)) else AiUiState.Idle
            _aiImprovedState.value = if (note.aiImprovedText != null) AiUiState.Success(TextImprovementResult.fromJson(note.aiImprovedText, note.content)) else AiUiState.Idle
        } else {
            // Reset states
            _aiSummaryState.value = AiUiState.Idle
            _aiTasksState.value = AiUiState.Idle
            _aiActionPlanState.value = AiUiState.Idle
            _aiMindMapState.value = AiUiState.Idle
            _aiIdeaState.value = AiUiState.Idle
            _aiStudyState.value = AiUiState.Idle
            _aiDecisionState.value = AiUiState.Idle
            _aiImprovedState.value = AiUiState.Idle
        }
    }

    fun createFolder(name: String, iconName: String = "Folder") {
        viewModelScope.launch {
            repository.insertFolder(Folder(name = name, iconName = iconName))
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            if (_selectedFolderId.value == folder.id) {
                _selectedFolderId.value = null
            }
        }
    }

    fun createNewNote(title: String = "Untitled Note", folderId: Int? = _selectedFolderId.value) {
        viewModelScope.launch {
            val newNote = Note(
                title = title,
                content = "",
                folderId = folderId,
                tags = "",
                lastModified = System.currentTimeMillis()
            )
            val id = repository.insertNote(newNote)
            val insertedNote = newNote.copy(id = id.toInt())
            selectNote(insertedNote)
        }
    }

    fun updateNoteTitle(title: String) {
        val current = _selectedNote.value ?: return
        val updated = current.copy(title = title, lastModified = System.currentTimeMillis())
        _selectedNote.value = updated
        viewModelScope.launch {
            repository.updateNote(updated)
        }
    }

    fun updateNoteContent(content: String) {
        val current = _selectedNote.value ?: return
        val updated = current.copy(content = content, lastModified = System.currentTimeMillis())
        _selectedNote.value = updated
        viewModelScope.launch {
            repository.updateNote(updated)
        }
    }

    fun updateNoteTags(tags: String) {
        val current = _selectedNote.value ?: return
        val updated = current.copy(tags = tags, lastModified = System.currentTimeMillis())
        _selectedNote.value = updated
        viewModelScope.launch {
            repository.updateNote(updated)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
            if (_selectedNote.value?.id == note.id) {
                selectNote(null)
            }
        }
    }

    // --- Core helper to check API validity and query Gemini ---
    private suspend fun queryGemini(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API Key is missing! Click the 'Secrets' panel in AI Studio and configure GEMINI_API_KEY with your key.")
        }
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )
        try {
            android.util.Log.d("SmartNotesAI", "Querying Gemini with prompt length: ${prompt.length}")
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (responseText == null) {
                android.util.Log.e("SmartNotesAI", "Received empty response candidate from Gemini.")
                throw Exception("Gemini returned an empty response.")
            }
            android.util.Log.d("SmartNotesAI", "Successfully received Gemini response.")
            return responseText
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            android.util.Log.e("SmartNotesAI", "Gemini API HttpException: Status Code ${e.code()}, Error Body: $errorBody", e)
            
            // Extract the user readable message field from the standard JSON error format returned by Google APIs
            val geminiMessage = try {
                val regex = "\"message\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                regex.find(errorBody)?.groupValues?.get(1)
            } catch (ignored: Exception) { null }

            val finalMessage = geminiMessage ?: errorBody.ifBlank { e.message() }
            throw Exception("Gemini API Error (HTTP ${e.code()}): $finalMessage")
        } catch (e: Exception) {
            android.util.Log.e("SmartNotesAI", "Gemini query failed with general exception", e)
            throw e
        }
    }

    private fun cleanJson(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```")
        }
        return cleaned.trim()
    }

    // --- AI TOOL 1: TEXT IMPROVEMENT ---
    fun improveText(mode: String) {
        val note = _selectedNote.value ?: return
        if (note.content.isBlank()) {
            _aiImprovedState.value = AiUiState.Error("Note content is blank. Please write something first!")
            return
        }
        _aiImprovedState.value = AiUiState.Loading
        viewModelScope.launch {
            try {
                val prompt = """
                    You are a writing improvement expert. Your task is to transform the following raw note content into high-quality improved writing suited for the requested style: $mode (Formal, Professional, Friendly, Concise, Persuasive).
                    
                    Output original text and improved text inside a structured JSON. Be precise.
                    
                    Respond ONLY with a valid JSON in this exact structure:
                    {
                      "original": "exactly original raw text",
                      "improved": "beautifully written improved version in $mode style",
                      "changesReason": "summary of vocabulary or syntax adjustments"
                    }
                    
                    Raw notes content:
                    "${note.content}"
                """.trimIndent()
                val rawResponse = queryGemini(prompt)
                val jsonStr = cleanJson(rawResponse)
                val result = TextImprovementResult.fromJson(jsonStr, note.content)
                _aiImprovedState.value = AiUiState.Success(result)
                
                // Cache inside DB
                val updated = note.copy(aiImprovedText = jsonStr)
                repository.updateNote(updated)
                _selectedNote.value = updated
            } catch (e: Exception) {
                _aiImprovedState.value = AiUiState.Error(e.localizedMessage ?: "Failed to improve text")
            }
        }
    }

    // --- AI TOOL 2: AI SUMMARY ---
    fun generateSummary() {
        val note = _selectedNote.value ?: return
        if (note.content.isBlank()) {
            _aiSummaryState.value = AiUiState.Error("Note content is empty. Add text to summarize!")
            return
        }
        _aiSummaryState.value = AiUiState.Loading
        viewModelScope.launch {
            try {
                val prompt = """
                    Analyze the following note content and compile a structured dashboard summary containing summary paragraph, a list of bulleted key points, important chronological dates, important mentioned names (with brief role or description), and operational action items.
                    
                    Respond ONLY with a valid JSON in this exact structure:
                    {
                      "summary": "a beautiful summary paragraph outlining the core themes",
                      "keyPoints": ["important point A", "important point B"],
                      "importantDates": ["Date X: explanation of occurrence", "Date Y: due dates described"],
                      "importantNames": ["Name Z: significance inside note", "Role/Person Y: assignment context"],
                      "actionItems": ["todo action items extracted in order", "task item B"]
                    }
                    
                    If a field (e.g. dates or names) is not mentioned in the note, please return an empty list [] for that key. Do not make up fake names or dates outside the text.
                    
                    Notes text:
                    "${note.content}"
                """.trimIndent()
                val rawResponse = queryGemini(prompt)
                val jsonStr = cleanJson(rawResponse)
                val result = SummaryResult.fromJson(jsonStr)
                _aiSummaryState.value = AiUiState.Success(result)
                
                // Cache inside DB
                val updated = note.copy(aiSummaryJson = jsonStr)
                repository.updateNote(updated)
                _selectedNote.value = updated
            } catch (e: Exception) {
                _aiSummaryState.value = AiUiState.Error(e.localizedMessage ?: "Failed to generate summary")
            }
        }
    }

    // --- AI TOOL 3: IDEA ORGANIZER ---
    fun organizeIdeas() {
        val note = _selectedNote.value ?: return
        if (note.content.isBlank()) {
            _aiIdeaState.value = AiUiState.Error("Note is empty. Add thoughts to organize!")
            return
        }
        _aiIdeaState.value = AiUiState.Loading
        viewModelScope.launch {
            try {
                val prompt = """
                    You are a cognitive organization strategist. Transform these messy brainstorming thoughts and textual details into a highly organized, expandable hierarchical tree structure. Include parents and children up to 2-3 levels.
                    
                    Respond ONLY with a valid JSON in this exact nested tree structure:
                    {
                      "title": "Main Central Business Idea or Brainstorm Subject",
                      "children": [
                        {
                          "title": "Category Theme A (e.g., Marketing)",
                          "children": [
                            { "title": "Subtopic element and detailed strategy A1", "children": [] },
                            { "title": "Subtopic element and detailed strategy A2", "children": [] }
                          ]
                        },
                        {
                          "title": "Category Theme B (e.g., Timeline)",
                          "children": []
                        }
                      ]
                    }
                    
                    Raw notes brainstorming dumps:
                    "${note.content}"
                """.trimIndent()
                val rawResponse = queryGemini(prompt)
                val jsonStr = cleanJson(rawResponse)
                val result = IdeaNode.fromJson(jsonStr)
                _aiIdeaState.value = AiUiState.Success(result)
                
                val updated = note.copy(aiIdeaJson = jsonStr)
                repository.updateNote(updated)
                _selectedNote.value = updated
            } catch (e: Exception) {
                _aiIdeaState.value = AiUiState.Error(e.localizedMessage ?: "Failed to organize ideas")
            }
        }
    }

    // --- AI TOOL 4: MIND MAP GENERATOR ---
    fun generateMindMap() {
        val note = _selectedNote.value ?: return
        if (note.content.isBlank()) {
            _aiMindMapState.value = AiUiState.Error("Note is empty. Type some content first.")
            return
        }
        _aiMindMapState.value = AiUiState.Loading
        viewModelScope.launch {
            try {
                val prompt = """
                    Convert the central themes of the following notes into an interactive visual mind map graph structure. Define unique string IDs, short conceptual node labels (under 3 words), category classification ('central', 'parent', 'child'), and distinctive visual hex colors (red, amber, green, blue themed codes). Declare connections as from -> to.
                    
                    Return ONLY a valid JSON in this exact structure:
                    {
                      "nodes": [
                        { "id": "1", "label": "Core Hub", "category": "central", "color": "#EF4444" },
                        { "id": "2", "label": "Key Idea A", "category": "parent", "color": "#F59E0B" },
                        { "id": "3", "label": "Subtopic A1", "category": "child", "color": "#3B82F6" }
                      ],
                      "edges": [
                        { "from": "1", "to": "2" },
                        { "from": "2", "to": "3" }
                      ]
                    }
                    
                    Notes text:
                    "${note.content}"
                """.trimIndent()
                val rawResponse = queryGemini(prompt)
                val jsonStr = cleanJson(rawResponse)
                val result = MindMapResult.fromJson(jsonStr)
                _aiMindMapState.value = AiUiState.Success(result)
                
                val updated = note.copy(aiMindMapJson = jsonStr)
                repository.updateNote(updated)
                _selectedNote.value = updated
            } catch (e: Exception) {
                _aiMindMapState.value = AiUiState.Error(e.localizedMessage ?: "Failed to generate mind map")
            }
        }
    }

    // --- AI TOOL 5: ACTION PLAN GENERATOR ---
    fun generateActionPlan() {
        val note = _selectedNote.value ?: return
        if (note.content.isBlank()) {
            _aiActionPlanState.value = AiUiState.Error("Notes are empty. Write goals to plan!")
            return
        }
        _aiActionPlanState.value = AiUiState.Loading
        viewModelScope.launch {
            try {
                val prompt = """
                    You are an executive project planner. Formulate a multi-stage execution roadmap layout (Phase 1, Phase 2, etc.) based on the goals and descriptions in the note. Provide objectives, deliverables, and duration estimates for each stage.
                    
                    Respond ONLY with a valid JSON array of roadmap objects in this structure:
                    [
                      {
                        "phase": "Phase 1: Research",
                        "objectives": ["Identify client needs", "Audit competitors"],
                        "deliverables": ["Detailed review spreadsheet", "Core design briefs"],
                        "duration": "2 weeks"
                      },
                      {
                        "phase": "Phase 2: Execution",
                        "objectives": ["Deploy code assets", "Write unit tests"],
                        "deliverables": ["Functional beta release"],
                        "duration": "3 weeks"
                      }
                    ]
                    
                    Note contents:
                    "${note.content}"
                """.trimIndent()
                val rawResponse = queryGemini(prompt)
                val jsonStr = cleanJson(rawResponse)
                val result = ActionPhase.fromJsonList(jsonStr)
                _aiActionPlanState.value = AiUiState.Success(result)
                
                val updated = note.copy(aiActionPlanJson = jsonStr)
                repository.updateNote(updated)
                _selectedNote.value = updated
            } catch (e: Exception) {
                _aiActionPlanState.value = AiUiState.Error(e.localizedMessage ?: "Failed to generate action plan")
            }
        }
    }

    // --- AI TOOL 6: TASK EXTRACTION ---
    fun extractTasks() {
        val note = _selectedNote.value ?: return
        if (note.content.isBlank()) {
            _aiTasksState.value = AiUiState.Error("Add some content to extract Kanban tasks!")
            return
        }
        _aiTasksState.value = AiUiState.Loading
        viewModelScope.launch {
            try {
                val prompt = """
                    Extract visual actionable tasks from the note to build a Kanban board. Assign status ('To Do', 'In Progress', 'Done'), priority level ('High', 'Medium', 'Low'), time estimate ('e.g. 15m', '1 hour', '2 days'), and reasonable due dates.
                    
                    Respond ONLY with a valid JSON array of tasks in this exact format:
                    [
                      {
                        "title": "Define operational strategy specifications",
                        "status": "To Do",
                        "priority": "High",
                        "estimatedTime": "1h",
                        "dueDate": "June 28"
                      },
                      {
                        "title": "Review outline notes",
                        "status": "In Progress",
                        "priority": "Medium",
                        "estimatedTime": "30m",
                        "dueDate": "Today"
                      }
                    ]
                    
                    Note text:
                    "${note.content}"
                """.trimIndent()
                val rawResponse = queryGemini(prompt)
                val jsonStr = cleanJson(rawResponse)
                val result = KanbanTask.fromJsonList(jsonStr)
                _aiTasksState.value = AiUiState.Success(result)
                
                val updated = note.copy(aiTasksJson = jsonStr)
                repository.updateNote(updated)
                _selectedNote.value = updated
            } catch (e: Exception) {
                _aiTasksState.value = AiUiState.Error(e.localizedMessage ?: "Failed to extract tasks")
            }
        }
    }

    // Action to move visual Kanban items locally
    fun updateTaskStatus(taskId: String, newStatus: String) {
        val currentState = _aiTasksState.value
        if (currentState is AiUiState.Success) {
            val updatedList = currentState.data.map {
                if (it.id == taskId) it.copy(status = newStatus) else it
            }
            _aiTasksState.value = AiUiState.Success(updatedList)
        }
    }

    // --- AI TOOL 7: STUDY MODE ---
    fun generateStudyMaterials() {
        val note = _selectedNote.value ?: return
        if (note.content.isBlank()) {
            _aiStudyState.value = AiUiState.Error("Note content is empty. Type concepts to study!")
            return
        }
        _aiStudyState.value = AiUiState.Loading
        viewModelScope.launch {
            try {
                val prompt = """
                    Analyze this note and compile interactive learning material:
                    1. A crisp study summary.
                    2. 3-4 digital flashcards (front is a direct question or prompt, back is the concise conceptual explanation).
                    3. 3 visual multiple-choice Quiz questions (with 4 answers options and zero-based index of correct option).
                    4. 3 key vocabularies or terms.
                    
                    Respond ONLY with valid JSON in this exact format structure:
                    {
                      "summary": "overview study guide summary sentence",
                      "flashcards": [
                        { "front": "Question A?", "back": "Term answer detail A" }
                      ],
                      "quiz": [
                        {
                          "question": "What is the primary theme discussed?",
                          "options": ["A Options", "B Options", "C Options", "D Options"],
                          "correctIndex": 0
                        }
                      ],
                      "concepts": [
                        { "name": "Deep Learning", "description": "Adaptive neural modeling frameworks" }
                      ]
                    }
                    
                    Note content:
                    "${note.content}"
                """.trimIndent()
                val rawResponse = queryGemini(prompt)
                val jsonStr = cleanJson(rawResponse)
                val result = StudyResult.fromJson(jsonStr)
                _aiStudyState.value = AiUiState.Success(result)
                
                val updated = note.copy(aiStudyJson = jsonStr)
                repository.updateNote(updated)
                _selectedNote.value = updated
            } catch (e: Exception) {
                _aiStudyState.value = AiUiState.Error(e.localizedMessage ?: "Failed to generate study mode")
            }
        }
    }

    // --- AI TOOL 8: DECISION ANALYZER ---
    fun analyzeDecision() {
        val note = _selectedNote.value ?: return
        if (note.content.isBlank()) {
            _aiDecisionState.value = AiUiState.Error("Notes are empty. Describe your decisions to analyze!")
            return
        }
        _aiDecisionState.value = AiUiState.Loading
        viewModelScope.launch {
            try {
                val prompt = """
                    You are an analytical decision scientist. Create a comparative decision matrix table evaluating different options, choices or approaches hinted or mentioned in this note. List pros, cons, risk factors, recommendation statement and rate each choice with an option score from 1 up to 10 points.
                    
                    Respond ONLY with a valid JSON array in this structural schema:
                    [
                      {
                        "option": "Name of Option A",
                        "pros": ["major advantage 1", "favorable point 2"],
                        "cons": ["downside 1", "hurdle 2"],
                        "risks": ["vulnerability to market or tech", "risk outline"],
                        "score": 9,
                        "recommendation": "Highly recommended due to robust flexibility factors."
                      }
                    ]
                    
                    If only one course of action is mentioned, create two optional approaches around it (e.g. Option A: Go All In immediately, Option B: Launch in Stages, Option C: Stay with status quo) so the comparison is fully informative.
                    
                    Notes text:
                    "${note.content}"
                """.trimIndent()
                val rawResponse = queryGemini(prompt)
                val jsonStr = cleanJson(rawResponse)
                val result = DecisionOption.fromJsonList(jsonStr)
                _aiDecisionState.value = AiUiState.Success(result)
                
                val updated = note.copy(aiDecisionJson = jsonStr)
                repository.updateNote(updated)
                _selectedNote.value = updated
            } catch (e: Exception) {
                _aiDecisionState.value = AiUiState.Error(e.localizedMessage ?: "Failed to analyze decision")
            }
        }
    }

    // --- AI TOOL 9: CHAT WITH NOTES ---
    fun sendChatMessage(queryText: String) {
        val note = _selectedNote.value
        if (queryText.isBlank()) return
        
        val userMsg = ChatMessage(sender = "user", text = queryText)
        _chatHistory.value = _chatHistory.value + userMsg
        _aiChatLoading.value = true

        viewModelScope.launch {
            try {
                val prompt = if (note != null) {
                    """
                        You are an intelligent Q&A chat assistant for "Smart Notes AI".
                        Help the user answer their query about the active note.
                        Active Note Title: "${note.title}"
                        Active Note Content:
                        "${note.content}"
                        
                        Other notes or tags context is: tags = "${note.tags}".
                        
                        Provide a polite, conversational, professional answer referencing the note. Be direct and clear. Keep the answer structured.
                        
                        Query: "$queryText"
                    """.trimIndent()
                } else {
                    """
                        You are an intelligent Q&A chat assistant for "Smart Notes AI".
                        The user does not have an active note selected. Clarify politely that they can select a note to converse explicitly about its text, but still answer their question general-purpose or helpfully.
                        
                        Query: "$queryText"
                    """.trimIndent()
                }
                
                val response = queryGemini(prompt)
                val citation = if (note != null) listOf(note.title) else emptyList<String>()
                val aiMsg = ChatMessage(sender = "ai", text = response, citations = citation)
                _chatHistory.value = _chatHistory.value + aiMsg
            } catch (e: Exception) {
                _chatHistory.value = _chatHistory.value + ChatMessage(sender = "ai", text = "Error: " + (e.localizedMessage ?: "Failed to generate chat answer."))
            } finally {
                _aiChatLoading.value = false
            }
        }
    }
}
