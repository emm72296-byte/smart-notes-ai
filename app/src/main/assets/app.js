// --- Smart Notes AI - Core Application Script ---

// Global Application State
let folders = [];
let notes = [];
let selectedFolderId = null;
let selectedNoteId = null;
let searchQuery = '';
let activeAiTool = null; // 1-9
let autosaveTimeout = null;

// Draggable Mind Map State
let mindmapNodes = [];
let mindmapEdges = [];
let draggedNode = null;
let canvasOffsetX = 0;
let canvasOffsetY = 0;
let lastMouseX = 0;
let lastMouseY = 0;

// Flashcard & Quiz local counters
let currentFlashcardIndex = 0;
let quizScore = 0;
let quizAnswered = {}; // questionIndex: selectedIndex

// Default Seeding Data
const defaultFolders = [
    { id: 1, name: "Personal", iconName: "Person" },
    { id: 2, name: "Work", iconName: "Work" },
    { id: 3, name: "Study", iconName: "Book" },
    { id: 4, name: "Brainstorm", iconName: "Lightbulb" }
];

const defaultNotes = [
    {
        id: 101,
        title: "Welcome to Smart Notes AI Workspace",
        content: `Welcome to the future of cognitive note-taking! This single-page application is fully self-contained, operates offline-first using localStorage, and calls the Google Gemini API directly from your browser.

Here is how to get started:
1. Tap the "Settings" gear icon in the top right of the sidebar and enter your Gemini API Key.
2. Select any folder (Personal, Work, Study, Brainstorm) from the chips above.
3. Tap "New Note" to create your own messy drafts or copy in existing texts.
4. Try out the 9 COGNITIVE AI TOOLS in the top row:
   - "Improve Writing" translates drafts into elegant styles.
   - "AI Summary" extracts dates, names, key points, and lists action items.
   - "Idea Organizer" forms expandable hierarchical concept trees.
   - "Mind Map" draws interactive draggable canvas networks.
   - "Action Plan" plots stage-based execution roadmaps.
   - "Task Boards" extracts automated Kanban tasks.
   - "Learn Labs" generates interactive multiple-choice quizzes and 3D flashcards.
   - "Decision Matrix" scores alternative options side-by-side.
   - "Workspace Chat" answers queries explicitly scoped to this note.

Have fun structuring your thoughts!`,
        folderId: 1,
        tags: "intro, cognitive, help, manual",
        lastModified: Date.now() - 3600000,
        aiSummaryJson: null,
        aiTasksJson: null,
        aiActionPlanJson: null,
        aiMindMapJson: null,
        aiIdeaJson: null,
        aiStudyJson: null,
        aiDecisionJson: null,
        aiImprovedText: null
    }
];

// SVG Icons mapping for dynamic list renderings
const svgIcons = {
    Folder: `<svg viewBox="0 0 24 24" class="icon"><path d="M10 4H4c-1.1 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2h-8l-2-2z" fill="currentColor"/></svg>`,
    Person: `<svg viewBox="0 0 24 24" class="icon"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" fill="currentColor"/></svg>`,
    Work: `<svg viewBox="0 0 24 24" class="icon"><path d="M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z" fill="currentColor"/></svg>`,
    Book: `<svg viewBox="0 0 24 24" class="icon"><path d="M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z" fill="currentColor"/></svg>`,
    Lightbulb: `<svg viewBox="0 0 24 24" class="icon"><path d="M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7zm2.85 11.1l-.85.6V16h-4v-1.3l-.85-.6C7.8 13.15 7 11.18 7 9c0-2.76 2.24-5 5-5s5 2.24 5 5c0 2.18-.8 4.15-2.15 5.1z" fill="currentColor"/></svg>`
};

// --- DATA LIFECYCLE (localStorage) ---

function initData() {
    if (!localStorage.getItem('smartnotes_folders')) {
        localStorage.setItem('smartnotes_folders', JSON.stringify(defaultFolders));
    }
    if (!localStorage.getItem('smartnotes_notes')) {
        localStorage.setItem('smartnotes_notes', JSON.stringify(defaultNotes));
    }
    
    folders = JSON.parse(localStorage.getItem('smartnotes_folders'));
    notes = JSON.parse(localStorage.getItem('smartnotes_notes'));
}

function saveToStorage() {
    localStorage.setItem('smartnotes_folders', JSON.stringify(folders));
    localStorage.setItem('smartnotes_notes', JSON.stringify(notes));
}

function getGeminiApiKey() {
    // Priority 1: Global window object injected by Android webview wrapper
    if (window.GEMINI_API_KEY && window.GEMINI_API_KEY !== "MY_GEMINI_API_KEY" && window.GEMINI_API_KEY.trim() !== "") {
        return window.GEMINI_API_KEY;
    }
    // Priority 2: localStorage key
    return localStorage.getItem('gemini_api_key') || "";
}

function saveGeminiApiKey(key) {
    localStorage.setItem('gemini_api_key', key);
}

// --- DOM REFERENCES & INITIALIZATION ---

document.addEventListener('DOMContentLoaded', () => {
    initData();
    setupEventListeners();
    renderFolders();
    renderNotes();
    
    // Check if there is an active note to display, if not show empty
    selectNote(null);
    
    // Check if Android WebView loaded, request key if empty
    setTimeout(() => {
        const key = getGeminiApiKey();
        if (key) {
            document.getElementById('api-key-input').value = key;
        }
    }, 500);
});

// --- SIDEBAR ACTIONS ---

function renderFolders() {
    const container = document.getElementById('folders-chip-container');
    container.innerHTML = '';
    
    // Render "All" Folder Chip
    const allChip = document.createElement('div');
    allChip.className = `folder-chip ${selectedFolderId === null ? 'active' : ''}`;
    allChip.innerHTML = `<span>All</span>`;
    allChip.onclick = () => {
        selectedFolderId = null;
        renderFolders();
        renderNotes();
    };
    container.appendChild(allChip);
    
    // Render distinct folder chips
    folders.forEach(folder => {
        const chip = document.createElement('div');
        chip.className = `folder-chip ${selectedFolderId === folder.id ? 'active' : ''}`;
        
        const folderIcon = svgIcons[folder.iconName] || svgIcons.Folder;
        chip.innerHTML = `
            ${folderIcon}
            <span>${folder.name}</span>
            <span class="folder-chip-delete" onclick="event.stopPropagation(); deleteFolder(${folder.id})">&times;</span>
        `;
        chip.onclick = () => {
            selectedFolderId = folder.id;
            renderFolders();
            renderNotes();
        };
        container.appendChild(chip);
    });
}

function renderNotes() {
    const container = document.getElementById('notes-list-container');
    container.innerHTML = '';
    
    // Filter notes
    let filteredNotes = notes;
    if (selectedFolderId !== null) {
        filteredNotes = filteredNotes.filter(n => n.folderId === selectedFolderId);
    }
    
    if (searchQuery.trim() !== '') {
        const query = searchQuery.toLowerCase().trim();
        filteredNotes = filteredNotes.filter(n => 
            n.title.toLowerCase().includes(query) ||
            n.content.toLowerCase().includes(query) ||
            n.tags.toLowerCase().includes(query)
        );
    }
    
    // Update Notes Count Header
    document.getElementById('notes-count-header').textContent = `NOTES (${filteredNotes.length})`;
    
    if (filteredNotes.length === 0) {
        container.innerHTML = `<div style="text-align: center; font-size: 12px; color: var(--text-muted); padding: 20px 0;">No notes found</div>`;
        return;
    }
    
    // Sort notes: most recently modified first
    filteredNotes.sort((a, b) => b.lastModified - a.lastModified);
    
    filteredNotes.forEach(note => {
        const card = document.createElement('div');
        card.className = `note-card ${selectedNoteId === note.id ? 'active' : ''}`;
        
        // Form tags preview
        let tagsPreview = '';
        if (note.tags && note.tags.trim() !== '') {
            const tagsList = note.tags.split(',').map(t => t.trim()).filter(t => t !== '');
            tagsPreview = tagsList.map(tag => `<span class="tag-badge">#${tag}</span>`).join(' ');
        }
        
        // Date formatting
        const dateStr = new Date(note.lastModified).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
        
        // Body snippet preview
        const bodySnippet = note.content ? note.content.substring(0, 100) : "Empty note...";
        
        card.innerHTML = `
            <div class="note-card-header">
                <span class="note-card-title">${note.title || "Untitled Note"}</span>
                <button class="note-card-delete" onclick="event.stopPropagation(); deleteNote(${note.id})" title="Delete Note">
                    <svg viewBox="0 0 24 24" class="small-icon"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" fill="currentColor"/></svg>
                </button>
            </div>
            <div class="note-card-body">${bodySnippet}</div>
            <div class="note-card-footer">
                <div class="note-card-tags">${tagsPreview}</div>
                <span class="note-card-date">${dateStr}</span>
            </div>
        `;
        
        card.onclick = () => selectNote(note.id);
        container.appendChild(card);
    });
}

function selectNote(id) {
    selectedNoteId = id;
    
    // Active styling on card
    const cards = document.querySelectorAll('.note-card');
    cards.forEach(card => card.classList.remove('active'));
    
    const activeCard = document.querySelector(`.note-card[data-id="${id}"]`);
    if (activeCard) activeCard.classList.add('active');
    
    const emptyState = document.getElementById('empty-state');
    const activeEditor = document.getElementById('active-editor');
    const aiPane = document.getElementById('ai-pane');
    
    if (id === null) {
        // Show empty screen
        emptyState.style.display = 'flex';
        activeEditor.style.display = 'none';
        aiPane.style.display = 'none';
        closeAiTools();
        return;
    }
    
    emptyState.style.display = 'none';
    activeEditor.style.display = 'flex';
    
    // Load note content into fields
    const note = notes.find(n => n.id === id);
    if (note) {
        document.getElementById('note-title-field').value = note.title || "";
        document.getElementById('note-tags-field').value = note.tags || "";
        document.getElementById('note-content-field').value = note.content || "";
        
        // Reset dynamic AI outputs / sync with loaded caches
        if (activeAiTool !== null) {
            renderAiToolView(activeAiTool);
        }
    }
    
    // Trigger desktop-mobile view shifts
    document.body.classList.remove('show-notes');
    document.body.classList.add('show-editor');
    syncMobileTabs();
}

function createNewNote() {
    const newId = Date.now();
    const newNote = {
        id: newId,
        title: "Untitled Note",
        content: "",
        folderId: selectedFolderId,
        tags: "",
        lastModified: Date.now()
    };
    notes.unshift(newNote);
    saveToStorage();
    renderNotes();
    selectNote(newId);
}

function deleteNote(id) {
    if (confirm("Are you sure you want to delete this note?")) {
        notes = notes.filter(n => n.id !== id);
        saveToStorage();
        renderNotes();
        if (selectedNoteId === id) {
            selectNote(null);
        }
    }
}

function createFolder(name, iconName) {
    if (!name || name.trim() === '') return;
    const newFolder = {
        id: Date.now(),
        name: name.trim(),
        iconName: iconName || "Folder"
    };
    folders.push(newFolder);
    saveToStorage();
    renderFolders();
}

function deleteFolder(id) {
    if (confirm("Are you sure you want to delete this folder? All notes inside will be kept in the general index.")) {
        // Remove folder
        folders = folders.filter(f => f.id !== id);
        // Clear notes associations
        notes = notes.map(n => {
            if (n.folderId === id) {
                return { ...n, folderId: null };
            }
            return n;
        });
        saveToStorage();
        if (selectedFolderId === id) {
            selectedFolderId = null;
        }
        renderFolders();
        renderNotes();
    }
}

// --- AUTOSAVE ENGINE & INPUT HANDLING ---

function triggerAutosave() {
    const statusText = document.getElementById('autosave-text');
    const statusWrapper = document.querySelector('.autosave-status');
    
    // Change UI state to "Saving..."
    statusText.textContent = "Saving...";
    statusWrapper.classList.add('autosave-saving');
    
    if (autosaveTimeout) clearTimeout(autosaveTimeout);
    
    autosaveTimeout = setTimeout(() => {
        if (selectedNoteId === null) return;
        
        const titleValue = document.getElementById('note-title-field').value;
        const tagsValue = document.getElementById('note-tags-field').value;
        const contentValue = document.getElementById('note-content-field').value;
        
        notes = notes.map(n => {
            if (n.id === selectedNoteId) {
                return {
                    ...n,
                    title: titleValue,
                    tags: tagsValue,
                    content: contentValue,
                    lastModified: Date.now()
                };
            }
            return n;
        });
        
        saveToStorage();
        renderNotes();
        
        // Highlight active card
        const card = document.querySelector(`.note-card[data-id="${selectedNoteId}"]`);
        if (card) card.classList.add('active');
        
        // Show saved confirmation
        statusText.textContent = "Auto-saved";
        statusWrapper.classList.remove('autosave-saving');
    }, 800);
}

// --- COGNITIVE AI TOOLS ROUTER ---

function openAiTool(toolNum) {
    activeAiTool = toolNum;
    
    // Highlight selected tool button
    const buttons = document.querySelectorAll('.ai-tool-btn');
    buttons.forEach(btn => {
        btn.classList.remove('active');
        if (parseInt(btn.getAttribute('data-tool')) === toolNum) {
            btn.classList.add('active');
        }
    });
    
    // Toggle side pane visibility
    const aiPane = document.getElementById('ai-pane');
    aiPane.style.display = 'flex';
    
    // Set Tool title
    const toolTitles = {
        1: "AI Writing Enhancer",
        2: "Dashboard Summary",
        3: "Cognitive Concept Tree",
        4: "Knowledge Graph Map",
        5: "Operational Roadmap",
        6: "Kanban Boards Extractor",
        7: "Learn Labs Study Center",
        8: "Decision Matrix",
        9: "Workspace Assistant"
    };
    document.getElementById('ai-tool-title').textContent = toolTitles[toolNum];
    
    // Redraw screen on mobile viewports
    document.body.classList.remove('show-editor');
    document.body.classList.add('show-ai');
    syncMobileTabs();
    
    // Render content
    renderAiToolView(toolNum);
}

function closeAiTools() {
    activeAiTool = null;
    document.getElementById('ai-pane').style.display = 'none';
    const buttons = document.querySelectorAll('.ai-tool-btn');
    buttons.forEach(btn => btn.classList.remove('active'));
    
    document.body.classList.remove('show-ai');
    document.body.classList.add('show-editor');
    syncMobileTabs();
}

function getNoteCacheKey(toolNum) {
    const keys = {
        1: "aiImprovedText",
        2: "aiSummaryJson",
        3: "aiIdeaJson",
        4: "aiMindMapJson",
        5: "aiActionPlanJson",
        6: "aiTasksJson",
        7: "aiStudyJson",
        8: "aiDecisionJson"
    };
    return keys[toolNum] || null;
}

function renderAiToolView(toolNum) {
    const container = document.getElementById('ai-content-body');
    container.innerHTML = '';
    
    const note = notes.find(n => n.id === selectedNoteId);
    if (!note) return;
    
    // Chat module (Tool 9) does not cache to the note directly
    if (toolNum === 9) {
        renderWorkspaceChat(container);
        return;
    }
    
    const cacheKey = getNoteCacheKey(toolNum);
    const cachedData = note[cacheKey];
    
    if (cachedData) {
        // Load and render parsed cached outputs directly
        try {
            const parsed = JSON.parse(cachedData);
            renderToolOutput(toolNum, parsed, container);
        } catch (e) {
            // Fallback for non-json (like text improvement string)
            if (toolNum === 1) {
                try {
                    const parsed = JSON.parse(cachedData);
                    renderToolOutput(1, parsed, container);
                } catch(e2) {
                    renderToolOutput(1, { original: note.content, improved: cachedData, changesReason: "Refined output style loaded from cache." }, container);
                }
            } else {
                renderIdleState(toolNum, container);
            }
        }
    } else {
        renderIdleState(toolNum, container);
    }
}

function renderIdleState(toolNum, container) {
    const iconSvgs = {
        1: `<svg viewBox="0 0 24 24" class="large-icon"><path d="M12.89 3L14.85 4.96L11.11 8.7L12.89 10.48L16.63 6.74L18.59 8.7V3H12.89Z" fill="currentColor"/></svg>`,
        2: `<svg viewBox="0 0 24 24" class="large-icon"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM14 17H7v-2h7v2z" fill="currentColor"/></svg>`,
        3: `<svg viewBox="0 0 24 24" class="large-icon"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6z" fill="currentColor"/></svg>`,
        4: `<svg viewBox="0 0 24 24" class="large-icon"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2z" fill="currentColor"/></svg>`,
        5: `<svg viewBox="0 0 24 24" class="large-icon"><path d="M19 15v4H5v-4h14" fill="currentColor"/></svg>`,
        6: `<svg viewBox="0 0 24 24" class="large-icon"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z" fill="currentColor"/></svg>`,
        7: `<svg viewBox="0 0 24 24" class="large-icon"><path d="M12 3L1 9l11 6 9-4.91V17h2V9L12 3z" fill="currentColor"/></svg>`,
        8: `<svg viewBox="0 0 24 24" class="large-icon"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z" fill="currentColor"/></svg>`
    };
    
    const descriptions = {
        1: "Enhances your messy writing into beautifully structured content.",
        2: "Extracts summary, key dates, names, and chronological action plans.",
        3: "Organizes brain dumps into nested, hierarchical concept trees.",
        4: "Maps themes into custom networks with coordinates.",
        5: "Builds structured stage-by-stage project timelines.",
        6: "Extracts an active Kanban task board with estimations.",
        7: "Creates flashcards, quizzes, and vocabulary cards.",
        8: "Evaluates alternatives with comparative matrix scoring."
    };
    
    const idleWrapper = document.createElement('div');
    idleWrapper.className = 'state-container';
    idleWrapper.innerHTML = `
        <div class="empty-icon-box" style="margin-bottom: 12px; width: 64px; height: 64px;">
            ${iconSvgs[toolNum] || ""}
        </div>
        <h4>Analysis Required</h4>
        <p style="margin-bottom: 20px;">${descriptions[toolNum]}</p>
    `;
    
    if (toolNum === 1) {
        // Text Improvement has a selection block
        const selectBlock = document.createElement('div');
        selectBlock.innerHTML = `
            <div class="improve-controls">
                <div class="improve-pill active" data-style="Formal">Formal</div>
                <div class="improve-pill" data-style="Professional">Professional</div>
                <div class="improve-pill" data-style="Friendly">Friendly</div>
                <div class="improve-pill" data-style="Concise">Concise</div>
                <div class="improve-pill" data-style="Persuasive">Persuasive</div>
            </div>
            <button id="run-ai-analysis-btn" class="btn btn-primary" style="width: 100%;">
                <svg viewBox="0 0 24 24" class="small-icon"><path d="M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9z" fill="currentColor"/></svg>
                <span>Enhance My Writing</span>
            </button>
        `;
        idleWrapper.appendChild(selectBlock);
        
        // Add event listeners to pills
        const pills = selectBlock.querySelectorAll('.improve-pill');
        pills.forEach(pill => {
            pill.onclick = () => {
                pills.forEach(p => p.classList.remove('active'));
                pill.classList.add('active');
            };
        });
        
        selectBlock.querySelector('#run-ai-analysis-btn').onclick = () => {
            const activeStyle = selectBlock.querySelector('.improve-pill.active').getAttribute('data-style');
            runCognitiveAnalysis(1, activeStyle);
        };
    } else {
        const runBtn = document.createElement('button');
        runBtn.className = 'btn btn-primary';
        runBtn.style.width = '100%';
        runBtn.innerHTML = `
            <svg viewBox="0 0 24 24" class="small-icon"><path d="M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9z" fill="currentColor"/></svg>
            <span>Query Cognitive Lab</span>
        `;
        runBtn.onclick = () => runCognitiveAnalysis(toolNum);
        idleWrapper.appendChild(runBtn);
    }
    
    container.appendChild(idleWrapper);
}

function renderLoadingState(container, message) {
    container.innerHTML = `
        <div class="state-container" style="padding: 60px 20px;">
            <div class="state-loading-spinner"></div>
            <h4>Consulting Cognitive Model...</h4>
            <p>${message || "Computing parameters, structural templates, and graph outputs."}</p>
        </div>
    `;
}

function renderErrorState(container, errMessage, toolNum) {
    container.innerHTML = `
        <div class="state-container">
            <svg viewBox="0 0 24 24" class="large-icon state-error-icon"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" fill="currentColor"/></svg>
            <h4 class="text-danger">Analysis Failed</h4>
            <p style="margin-bottom: 20px; font-size: 12.5px; color: var(--text-secondary); line-height: 1.5;">${errMessage}</p>
            <button id="retry-ai-analysis-btn" class="btn btn-secondary" style="width: 100%;">
                <span>Retry Connection</span>
            </button>
        </div>
    `;
    container.querySelector('#retry-ai-analysis-btn').onclick = () => {
        if (toolNum === 1) {
            renderAiToolView(1); // will trigger choice pill reloading
        } else {
            runCognitiveAnalysis(toolNum);
        }
    };
}

// --- RENDERING PARSED MODEL TEMPLATES ---

function renderToolOutput(toolNum, parsedData, container) {
    container.innerHTML = '';
    
    switch (toolNum) {
        case 1: // Writing enhancer
            const improvementCard = document.createElement('div');
            improvementCard.className = 'ai-improvement-card';
            improvementCard.innerHTML = `
                <div class="ai-improvement-section">
                    <div class="improvement-label">ORIGINAL SKETCH</div>
                    <div class="improvement-text">${parsedData.original || ""}</div>
                </div>
                <div class="ai-improvement-section" style="margin-top: 16px;">
                    <div class="improvement-label" style="color: var(--accent-secondary);">AI COGNITIVE REFINEMENT</div>
                    <div class="improvement-text refined">${parsedData.improved || ""}</div>
                </div>
                <div class="ai-improvement-section" style="margin-top: 16px;">
                    <div class="improvement-label" style="color: var(--accent-tertiary);">RATIONALE BRIEF</div>
                    <div class="improvement-rationale">${parsedData.changesReason || "Vocabulary, phrasing syntax alignments."}</div>
                </div>
                <div style="display: flex; gap: 8px; margin-top: 24px;">
                    <button id="apply-improved-writing-btn" class="btn btn-primary" style="flex-grow: 1; font-size: 12.5px;">
                        Apply to Note
                    </button>
                    <button id="reset-improved-writing-btn" class="btn btn-secondary" style="padding: 10px;">
                        Re-Analyze
                    </button>
                </div>
            `;
            container.appendChild(improvementCard);
            
            // Apply refinement
            improvementCard.querySelector('#apply-improved-writing-btn').onclick = () => {
                if (parsedData.improved) {
                    document.getElementById('note-content-field').value = parsedData.improved;
                    triggerAutosave();
                    alert("Cognitive writing improvement applied successfully!");
                }
            };
            
            // Re-analyze
            improvementCard.querySelector('#reset-improved-writing-btn').onclick = () => {
                const note = notes.find(n => n.id === selectedNoteId);
                if (note) {
                    note.aiImprovedText = null;
                    saveToStorage();
                    renderAiToolView(1);
                }
            };
            break;
            
        case 2: // AI Summary
            const summaryDiv = document.createElement('div');
            summaryDiv.className = 'summary-container';
            
            // 1. Dashboard summary paragraph
            summaryDiv.innerHTML = `
                <div class="summary-main-para">
                    <div class="improvement-label" style="color: var(--accent-secondary); margin-bottom: 4px;">EXECUTIVE SYNOPSIS</div>
                    <p style="font-size: 13.5px; line-height: 1.6;">${parsedData.summary || "No synopsis available."}</p>
                </div>
            `;
            
            // 2. Key Points
            if (parsedData.keyPoints && parsedData.keyPoints.length > 0) {
                const kpCard = document.createElement('div');
                kpCard.className = 'summary-grid-card';
                kpCard.innerHTML = `
                    <div class="summary-grid-title">CORE TAKEAWAYS</div>
                    <ul class="summary-list">
                        ${parsedData.keyPoints.map(p => `<li>${p}</li>`).join('')}
                    </ul>
                `;
                summaryDiv.appendChild(kpCard);
            }
            
            // 3. Dates
            if (parsedData.importantDates && parsedData.importantDates.length > 0) {
                const datesCard = document.createElement('div');
                datesCard.className = 'summary-grid-card';
                datesCard.innerHTML = `
                    <div class="summary-grid-title" style="color: var(--accent-tertiary);">CHRONOLOGY & DUE DATES</div>
                    <ul class="summary-list summary-dates-list">
                        ${parsedData.importantDates.map(d => `<li>${d}</li>`).join('')}
                    </ul>
                `;
                summaryDiv.appendChild(datesCard);
            }
            
            // 4. Names
            if (parsedData.importantNames && parsedData.importantNames.length > 0) {
                const namesCard = document.createElement('div');
                namesCard.className = 'summary-grid-card';
                namesCard.innerHTML = `
                    <div class="summary-grid-title" style="color: var(--accent-primary);">STAKEHOLDERS & ENTITIES</div>
                    <ul class="summary-list summary-names-list">
                        ${parsedData.importantNames.map(n => `<li>${n}</li>`).join('')}
                    </ul>
                `;
                summaryDiv.appendChild(namesCard);
            }
            
            // 5. Action Items
            if (parsedData.actionItems && parsedData.actionItems.length > 0) {
                const actionCard = document.createElement('div');
                actionCard.className = 'summary-grid-card';
                actionCard.innerHTML = `
                    <div class="summary-grid-title" style="color: var(--success);">EXTRACTED ACTION ITEMS</div>
                    <ul class="summary-list" style="padding-left: 0;">
                        ${parsedData.actionItems.map((item, idx) => `
                            <li style="list-style: none; padding-left: 28px; position: relative; cursor: pointer; display: flex; align-items: center; margin: 4px 0;" onclick="this.classList.toggle('done'); this.querySelector('input').checked = !this.querySelector('input').checked;">
                                <input type="checkbox" style="position: absolute; left: 4px; top: 10px; cursor: pointer;" onclick="event.stopPropagation();">
                                <span style="font-size: 13px;">${item}</span>
                            </li>
                        `).join('')}
                    </ul>
                `;
                summaryDiv.appendChild(actionCard);
            }
            
            // Clear cache option
            const clearSumBtn = document.createElement('button');
            clearSumBtn.className = 'btn btn-secondary btn-sm';
            clearSumBtn.style.width = '100%';
            clearSumBtn.style.marginTop = '10px';
            clearSumBtn.textContent = "Recalculate Summary";
            clearSumBtn.onclick = () => resetToolCache(2);
            summaryDiv.appendChild(clearSumBtn);
            
            container.appendChild(summaryDiv);
            break;
            
        case 3: // Expandable Hierarchical Concept Tree
            const treeDiv = document.createElement('div');
            treeDiv.className = 'tree-container';
            treeDiv.style.padding = '10px 0';
            
            function buildTreeNodeHtml(node) {
                let childrenHtml = '';
                if (node.children && node.children.length > 0) {
                    childrenHtml = `<div class="tree-node-wrapper">
                        ${node.children.map(child => buildTreeNodeHtml(child)).join('')}
                    </div>`;
                }
                
                return `
                    <div class="tree-node-row">
                        <div class="tree-node">
                            <span class="tree-node-title">${node.title || "Concept Node"}</span>
                        </div>
                        ${childrenHtml}
                    </div>
                `;
            }
            
            treeDiv.innerHTML = buildTreeNodeHtml(parsedData);
            
            // Clear cache option
            const clearTreeBtn = document.createElement('button');
            clearTreeBtn.className = 'btn btn-secondary btn-sm';
            clearTreeBtn.style.width = '100%';
            clearTreeBtn.style.marginTop = '20px';
            clearTreeBtn.textContent = "Rebuild Concept Tree";
            clearTreeBtn.onclick = () => resetToolCache(3);
            treeDiv.appendChild(clearTreeBtn);
            
            container.appendChild(treeDiv);
            break;
            
        case 4: // Knowledge Graph Mind Map
            const mindmapWrapper = document.createElement('div');
            mindmapWrapper.id = 'mindmap-inner-wrapper';
            mindmapWrapper.innerHTML = `
                <div id="mindmap-container">
                    <canvas id="mindmap-canvas"></canvas>
                    <div class="canvas-instruction">Drag circles to rearrange. Pinch/Scroll to zoom.</div>
                </div>
                <button id="reset-mindmap-btn" class="btn btn-secondary btn-sm" style="width: 100%; margin-top: 14px;">
                    Re-Generate Knowledge Graph
                </button>
            `;
            container.appendChild(mindmapWrapper);
            
            mindmapWrapper.querySelector('#reset-mindmap-btn').onclick = () => resetToolCache(4);
            
            // Start Canvas Engine
            setTimeout(() => {
                initMindMapCanvas(parsedData);
            }, 100);
            break;
            
        case 5: // Stage based roadmap timeline
            const roadmapDiv = document.createElement('div');
            roadmapDiv.className = 'timeline';
            
            const stages = Array.isArray(parsedData) ? parsedData : [];
            stages.forEach((stage, sIdx) => {
                const item = document.createElement('div');
                item.className = 'timeline-stage';
                item.innerHTML = `
                    <div class="timeline-dot"></div>
                    <div class="timeline-card">
                        <div class="timeline-card-header">
                            <span class="timeline-phase" style="color: ${sIdx % 2 === 0 ? 'var(--accent-secondary)' : 'var(--accent-tertiary)'}">${stage.phase || `Phase ${sIdx + 1}`}</span>
                            <span class="timeline-duration">${stage.duration || "TBD"}</span>
                        </div>
                        ${stage.objectives && stage.objectives.length > 0 ? `
                            <div class="timeline-block-title">KEY OBJECTIVES</div>
                            <ul class="summary-list">
                                ${stage.objectives.map(o => `<li>${o}</li>`).join('')}
                            </ul>
                        ` : ''}
                        ${stage.deliverables && stage.deliverables.length > 0 ? `
                            <div class="timeline-block-title">CORE DELIVERABLES</div>
                            <ul class="summary-list">
                                ${stage.deliverables.map(d => `<li style="color: var(--text-secondary);">${d}</li>`).join('')}
                            </ul>
                        ` : ''}
                    </div>
                `;
                roadmapDiv.appendChild(item);
            });
            
            const timelineClear = document.createElement('button');
            timelineClear.className = 'btn btn-secondary btn-sm';
            timelineClear.style.width = '100%';
            timelineClear.style.marginTop = '14px';
            timelineClear.textContent = "Replan Project Roadmap";
            timelineClear.onclick = () => resetToolCache(5);
            
            const roadmapWrapper = document.createElement('div');
            roadmapWrapper.appendChild(roadmapDiv);
            roadmapWrapper.appendChild(timelineClear);
            container.appendChild(roadmapWrapper);
            break;
            
        case 6: // Kanban Task Boards
            const kanbanWrapperDiv = document.createElement('div');
            kanbanWrapperDiv.className = 'kanban-wrapper';
            
            // Filter columns
            const statuses = ["To Do", "In Progress", "Done"];
            const rawTasks = Array.isArray(parsedData) ? parsedData : [];
            
            // Format task cards locally
            statuses.forEach(status => {
                const colTasks = rawTasks.filter(t => t.status === status);
                const col = document.createElement('div');
                col.className = 'kanban-column';
                col.innerHTML = `
                    <div class="kanban-column-title">
                        <span>${status.toUpperCase()}</span>
                        <span class="kanban-count-badge">${colTasks.length}</span>
                    </div>
                    <div class="kanban-list" data-status="${status}">
                        ${colTasks.map((task, index) => {
                            const priorityClass = `priority-${(task.priority || "Medium").toLowerCase()}`;
                            return `
                                <div class="kanban-card" data-title="${task.title}" data-priority="${task.priority}">
                                    <div class="kanban-card-title">${task.title || "Task item"}</div>
                                    <div class="kanban-card-meta">
                                        <span class="priority-badge ${priorityClass}">${task.priority || "Medium"}</span>
                                        ${task.estimatedTime ? `<span class="kanban-time-pill">${task.estimatedTime}</span>` : ''}
                                        ${task.dueDate ? `<span class="kanban-time-pill">📅 ${task.dueDate}</span>` : ''}
                                    </div>
                                    <div class="kanban-card-actions">
                                        ${status !== "To Do" ? `<button class="kanban-action-btn" onclick="shiftKanbanTask('${task.title}', '${status}', -1)">&larr; Move</button>` : ''}
                                        ${status !== "Done" ? `<button class="kanban-action-btn" onclick="shiftKanbanTask('${task.title}', '${status}', 1)">Move &rarr;</button>` : ''}
                                    </div>
                                </div>
                            `;
                        }).join('')}
                        ${colTasks.length === 0 ? `<div style="font-size: 11px; text-align: center; color: var(--text-muted); padding: 12px 0;">No tasks</div>` : ''}
                    </div>
                `;
                kanbanWrapperDiv.appendChild(col);
            });
            
            const clearKanbanBtn = document.createElement('button');
            clearKanbanBtn.className = 'btn btn-secondary btn-sm';
            clearKanbanBtn.style.width = '100%';
            clearKanbanBtn.style.marginTop = '10px';
            clearKanbanBtn.textContent = "Refresh Kanban Tasks";
            clearKanbanBtn.onclick = () => resetToolCache(6);
            
            kanbanWrapperDiv.appendChild(clearKanbanBtn);
            container.appendChild(kanbanWrapperDiv);
            break;
            
        case 7: // Study Center
            const studyWrapper = document.createElement('div');
            studyWrapper.className = 'study-container';
            studyWrapper.innerHTML = `
                <div class="study-nav-tabs">
                    <button class="study-nav-tab active" data-tab="guide">Study Guide</button>
                    <button class="study-nav-tab" data-tab="flashcards">Flashcards</button>
                    <button class="study-nav-tab" data-tab="quiz">Active Quiz</button>
                </div>
                <div id="study-tab-body" class="study-tab-content">
                    <!-- Tab views are injected here dynamically -->
                </div>
                <button id="reset-study-btn" class="btn btn-secondary btn-sm" style="width: 100%; margin-top: 18px;">
                    Re-Generate Study Material
                </button>
            `;
            container.appendChild(studyWrapper);
            studyWrapper.querySelector('#reset-study-btn').onclick = () => resetToolCache(7);
            
            const studyTabs = studyWrapper.querySelectorAll('.study-nav-tab');
            studyTabs.forEach(tab => {
                tab.onclick = () => {
                    studyTabs.forEach(t => t.classList.remove('active'));
                    tab.classList.add('active');
                    const targetTab = tab.getAttribute('data-tab');
                    renderStudyTab(targetTab, parsedData);
                };
            });
            
            // Render Initial Guide Tab
            renderStudyTab('guide', parsedData);
            break;
            
        case 8: // Decision Analyzer
            const decisionContainer = document.createElement('div');
            
            const options = Array.isArray(parsedData) ? parsedData : [];
            options.forEach(opt => {
                const card = document.createElement('div');
                card.className = 'decision-card';
                
                // Score evaluation styles
                const score = opt.score || 5;
                let scoreClass = 'score-warning';
                if (score >= 8) scoreClass = 'score-excellent';
                
                card.innerHTML = `
                    <div class="decision-card-header">
                        <span class="decision-option-title">${opt.option || "Course of Action"}</span>
                        <div class="decision-score-circle ${scoreClass}">${score}</div>
                    </div>
                    ${opt.pros && opt.pros.length > 0 ? `
                        <div class="decision-list-group">
                            <div class="improvement-label" style="color: var(--success);">PROS / ADVANTAGES</div>
                            <ul class="decision-bullet-list decision-pros">
                                ${opt.pros.map(p => `<li>${p}</li>`).join('')}
                            </ul>
                        </div>
                    ` : ''}
                    ${opt.cons && opt.cons.length > 0 ? `
                        <div class="decision-list-group" style="margin-top: 10px;">
                            <div class="improvement-label" style="color: var(--danger);">CONS / DRAWBACKS</div>
                            <ul class="decision-bullet-list decision-cons">
                                ${opt.cons.map(c => `<li>${c}</li>`).join('')}
                            </ul>
                        </div>
                    ` : ''}
                    ${opt.risks && opt.risks.length > 0 ? `
                        <div class="decision-list-group" style="margin-top: 10px;">
                            <div class="improvement-label" style="color: var(--warning);">RISK PROFILE SUMMARY</div>
                            <ul class="decision-bullet-list decision-risks">
                                ${opt.risks.map(r => `<li>${r}</li>`).join('')}
                            </ul>
                        </div>
                    ` : ''}
                    <div class="decision-rec">
                        <div class="improvement-label" style="color: var(--text-muted); margin-bottom: 2px;">STRATEGIC RECOMMENDATION</div>
                        <p style="font-size: 12.5px; line-height: 1.5;">${opt.recommendation || "Proceed with standard execution parameters."}</p>
                    </div>
                `;
                decisionContainer.appendChild(card);
            });
            
            const clearDecisionBtn = document.createElement('button');
            clearDecisionBtn.className = 'btn btn-secondary btn-sm';
            clearDecisionBtn.style.width = '100%';
            clearDecisionBtn.style.marginTop = '10px';
            clearDecisionBtn.textContent = "Refresh Decision Scores";
            clearDecisionBtn.onclick = () => resetToolCache(8);
            
            decisionContainer.appendChild(clearDecisionBtn);
            container.appendChild(decisionContainer);
            break;
    }
}

function resetToolCache(toolNum) {
    const note = notes.find(n => n.id === selectedNoteId);
    if (note) {
        const cacheKey = getNoteCacheKey(toolNum);
        note[cacheKey] = null;
        saveToStorage();
        renderAiToolView(toolNum);
    }
}

// --- CANVAS MIND MAP ENGINE ---

function initMindMapCanvas(data) {
    const canvas = document.getElementById('mindmap-canvas');
    if (!canvas) return;
    
    // Size to container bounding rect
    const container = document.getElementById('mindmap-container');
    const rect = container.getBoundingClientRect();
    canvas.width = rect.width;
    canvas.height = rect.height;
    
    const ctx = canvas.getContext('2d');
    
    // Parse Nodes & Edges from data
    const rawNodes = data.nodes || [];
    const rawEdges = data.edges || [];
    
    // Set coordinates if not set
    mindmapNodes = rawNodes.map((n, idx) => {
        const angle = (idx / rawNodes.length) * Math.PI * 2;
        const radius = n.category === 'central' ? 0 : 130;
        
        return {
            id: n.id,
            label: n.label || "Node",
            category: n.category || "child",
            color: n.color || "#6366f1",
            x: canvas.width / 2 + Math.cos(angle) * radius,
            y: canvas.height / 2 + Math.sin(angle) * radius,
            radius: n.category === 'central' ? 50 : 42
        };
    });
    
    mindmapEdges = rawEdges;
    
    drawMindMap(canvas, ctx);
    
    // Add Mouse and Touch Interaction for dragging nodes around
    function getEventPos(e) {
        const cRect = canvas.getBoundingClientRect();
        if (e.touches && e.touches.length > 0) {
            return {
                x: e.touches[0].clientX - cRect.left,
                y: e.touches[0].clientY - cRect.top
            };
        }
        return {
            x: e.clientX - cRect.left,
            y: e.clientY - cRect.top
        };
    }
    
    function handleDown(e) {
        const pos = getEventPos(e);
        // Check if user clicked inside a node
        for (let i = mindmapNodes.length - 1; i >= 0; i--) {
            const node = mindmapNodes[i];
            const dist = Math.sqrt((pos.x - node.x) ** 2 + (pos.y - node.y) ** 2);
            if (dist <= node.radius) {
                draggedNode = node;
                canvas.style.cursor = 'grabbing';
                break;
            }
        }
    }
    
    function handleMove(e) {
        if (!draggedNode) return;
        const pos = getEventPos(e);
        draggedNode.x = Math.max(draggedNode.radius, Math.min(canvas.width - draggedNode.radius, pos.x));
        draggedNode.y = Math.max(draggedNode.radius, Math.min(canvas.height - draggedNode.radius, pos.y));
        drawMindMap(canvas, ctx);
    }
    
    function handleUp() {
        draggedNode = null;
        canvas.style.cursor = 'grab';
    }
    
    canvas.addEventListener('mousedown', handleDown);
    canvas.addEventListener('mousemove', handleMove);
    canvas.addEventListener('mouseup', handleUp);
    canvas.addEventListener('mouseleave', handleUp);
    
    canvas.addEventListener('touchstart', (e) => { e.preventDefault(); handleDown(e); });
    canvas.addEventListener('touchmove', (e) => { e.preventDefault(); handleMove(e); });
    canvas.addEventListener('touchend', handleUp);
}

function drawMindMap(canvas, ctx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // 1. Draw Edges (Connections)
    ctx.strokeStyle = '#2a354f';
    ctx.lineWidth = 2.5;
    ctx.setLineDash([5, 5]); // attractive dashed flow
    
    mindmapEdges.forEach(edge => {
        const fromNode = mindmapNodes.find(n => n.id === edge.from);
        const toNode = mindmapNodes.find(n => n.id === edge.to);
        
        if (fromNode && toNode) {
            ctx.beginPath();
            ctx.moveTo(fromNode.x, fromNode.y);
            ctx.lineTo(toNode.x, toNode.y);
            ctx.stroke();
        }
    });
    
    ctx.setLineDash([]); // Reset line dashes
    
    // 2. Draw Nodes
    mindmapNodes.forEach(node => {
        // Draw glow effect for active dragging
        if (draggedNode === node) {
            ctx.shadowBlur = 15;
            ctx.shadowColor = node.color;
        } else {
            ctx.shadowBlur = 6;
            ctx.shadowColor = 'rgba(0, 0, 0, 0.4)';
        }
        
        // Draw node background circle
        ctx.fillStyle = '#0f172a';
        ctx.strokeStyle = node.color;
        ctx.lineWidth = 3.5;
        
        ctx.beginPath();
        ctx.arc(node.x, node.y, node.radius, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        
        ctx.shadowBlur = 0; // Reset shadow for text rendering
        
        // Draw central hub badge indicator
        if (node.category === 'central') {
            ctx.fillStyle = node.color;
            ctx.beginPath();
            ctx.arc(node.x, node.y - node.radius/2 - 2, 4, 0, Math.PI * 2);
            ctx.fill();
        }
        
        // Wrap & Render Text Label inside Node
        ctx.fillStyle = '#f8fafc';
        ctx.font = '700 10.5px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        
        const words = node.label.split(' ');
        if (words.length > 2) {
            ctx.fillText(words.slice(0, 2).join(' '), node.x, node.y - 6);
            ctx.fillText(words.slice(2).join(' '), node.x, node.y + 6);
        } else if (words.length === 2) {
            ctx.fillText(words[0], node.x, node.y - 6);
            ctx.fillText(words[1], node.x, node.y + 6);
        } else {
            ctx.fillText(node.label, node.x, node.y);
        }
    });
}

// --- LEARN LABS STUDY CENTER ENGINE ---

function renderStudyTab(tabName, parsedData) {
    const container = document.getElementById('study-tab-body');
    container.innerHTML = '';
    
    switch (tabName) {
        case 'guide':
            // Render Study Synopsis
            const synopsis = document.createElement('div');
            synopsis.className = 'summary-main-para';
            synopsis.innerHTML = `
                <div class="improvement-label" style="color: var(--accent-secondary); margin-bottom: 4px;">STUDY SYNOPSIS</div>
                <p style="font-size: 13px; line-height: 1.6;">${parsedData.summary || "No conceptual synopsis generated."}</p>
            `;
            container.appendChild(synopsis);
            
            // Render Vocabulary Terms list
            if (parsedData.concepts && parsedData.concepts.length > 0) {
                const conceptsHeader = document.createElement('div');
                conceptsHeader.className = 'improvement-label';
                conceptsHeader.style.color = 'var(--text-secondary)';
                conceptsHeader.textContent = "KEY TERMS & DEFINITIONS";
                container.appendChild(conceptsHeader);
                
                parsedData.concepts.forEach(vocab => {
                    const card = document.createElement('div');
                    card.className = 'vocab-card';
                    card.innerHTML = `
                        <div class="vocab-name">${vocab.name || "Term"}</div>
                        <div class="vocab-desc">${vocab.description || "Conceptual explanation."}</div>
                    `;
                    container.appendChild(card);
                });
            }
            break;
            
        case 'flashcards':
            const cards = parsedData.flashcards || [];
            if (cards.length === 0) {
                container.innerHTML = `<div style="text-align: center; color: var(--text-muted); font-size: 12px; padding: 20px 0;">No flashcards available.</div>`;
                return;
            }
            
            // Render Slide container
            if (currentFlashcardIndex >= cards.length) currentFlashcardIndex = 0;
            const currentCard = cards[currentFlashcardIndex];
            
            const cardSlide = document.createElement('div');
            cardSlide.innerHTML = `
                <div class="flashcard-wrapper" onclick="this.classList.toggle('flipped')">
                    <div class="flashcard-inner">
                        <div class="flashcard-front">
                            <span class="flashcard-label">FLASHCARD ${currentFlashcardIndex + 1} • CLICK TO FLIP</span>
                            <p class="flashcard-text">${currentCard.front || "Concept Question?"}</p>
                        </div>
                        <div class="flashcard-back">
                            <span class="flashcard-label" style="color: var(--accent-secondary);">EXPLANATION BRIEF</span>
                            <p class="flashcard-text" style="font-size: 13px; font-weight: 500;">${currentCard.back || "Conceptual explanation details."}</p>
                        </div>
                    </div>
                </div>
                <div class="flashcard-nav" style="margin-top: 14px;">
                    <button id="prev-flashcard-btn" class="btn btn-secondary btn-sm" ${currentFlashcardIndex === 0 ? 'disabled' : ''}>&larr; Prev</button>
                    <span class="flashcard-indicator">${currentFlashcardIndex + 1} of ${cards.length}</span>
                    <button id="next-flashcard-btn" class="btn btn-secondary btn-sm" ${currentFlashcardIndex === cards.length - 1 ? 'disabled' : ''}>Next &rarr;</button>
                </div>
            `;
            container.appendChild(cardSlide);
            
            cardSlide.querySelector('#prev-flashcard-btn').onclick = () => {
                if (currentFlashcardIndex > 0) {
                    currentFlashcardIndex--;
                    renderStudyTab('flashcards', parsedData);
                }
            };
            
            cardSlide.querySelector('#next-flashcard-btn').onclick = () => {
                if (currentFlashcardIndex < cards.length - 1) {
                    currentFlashcardIndex++;
                    renderStudyTab('flashcards', parsedData);
                }
            };
            break;
            
        case 'quiz':
            const questions = parsedData.quiz || [];
            if (questions.length === 0) {
                container.innerHTML = `<div style="text-align: center; color: var(--text-muted); font-size: 12px; padding: 20px 0;">No Quiz questions available.</div>`;
                return;
            }
            
            // Build Quiz List
            const quizListContainer = document.createElement('div');
            quizListContainer.style.display = 'flex';
            quizListContainer.style.flexDirection = 'column';
            quizListContainer.style.gap = '16px';
            
            questions.forEach((q, qIdx) => {
                const card = document.createElement('div');
                card.className = 'quiz-card';
                
                const answeredIdx = quizAnswered[qIdx];
                const isAnswered = answeredIdx !== undefined;
                
                card.innerHTML = `
                    <div class="quiz-question">${qIdx + 1}. ${q.question || "Study Query?"}</div>
                    <div class="quiz-options-list">
                        ${(q.options || []).map((opt, oIdx) => {
                            let optionClass = '';
                            if (isAnswered) {
                                if (oIdx === q.correctIndex) optionClass = 'correct';
                                else if (oIdx === answeredIdx) optionClass = 'incorrect';
                            }
                            return `
                                <button class="quiz-option-btn ${optionClass}" data-q="${qIdx}" data-o="${oIdx}" ${isAnswered ? 'disabled' : ''}>
                                    ${opt}
                                </button>
                            `;
                        }).join('')}
                    </div>
                    ${isAnswered ? `
                        <div class="quiz-feedback" style="color: ${answeredIdx === q.correctIndex ? 'var(--success)' : 'var(--danger)'}">
                            ${answeredIdx === q.correctIndex ? '✓ Correct Answer!' : `✗ Incorrect (Correct Option is "${q.options[q.correctIndex]}")`}
                        </div>
                    ` : ''}
                `;
                
                // Click events for options
                card.querySelectorAll('.quiz-option-btn').forEach(btn => {
                    btn.onclick = () => {
                        const qNum = parseInt(btn.getAttribute('data-q'));
                        const oNum = parseInt(btn.getAttribute('data-o'));
                        
                        // Register answer
                        quizAnswered[qNum] = oNum;
                        if (oNum === q.correctIndex) {
                            quizScore++;
                        }
                        
                        renderStudyTab('quiz', parsedData);
                    };
                });
                
                quizListContainer.appendChild(card);
            });
            
            // Score Board Summary
            const totalAnsweredCount = Object.keys(quizAnswered).length;
            if (totalAnsweredCount > 0) {
                const scoreBoard = document.createElement('div');
                scoreBoard.className = 'summary-main-para';
                scoreBoard.style.backgroundColor = 'var(--bg-dark)';
                scoreBoard.style.borderStyle = 'dashed';
                scoreBoard.style.textAlign = 'center';
                scoreBoard.innerHTML = `
                    <div class="improvement-label" style="color: var(--accent-secondary);">QUIZ BOARD SCORE</div>
                    <h3 style="font-size: 22px; margin-top: 4px;">${quizScore} / ${questions.length}</h3>
                    <p style="font-size: 11px; color: var(--text-secondary); margin-bottom: 12px;">Completed ${totalAnsweredCount} of ${questions.length} questions.</p>
                    <button id="reset-quiz-score-btn" class="btn btn-secondary btn-sm" style="width: 100%;">Reset Quiz Answers</button>
                `;
                quizListContainer.appendChild(scoreBoard);
                
                scoreBoard.querySelector('#reset-quiz-score-btn').onclick = () => {
                    quizScore = 0;
                    quizAnswered = {};
                    renderStudyTab('quiz', parsedData);
                };
            }
            
            container.appendChild(quizListContainer);
            break;
    }
}

// --- LOCAL KANBAN DATA MUTATION ---

function shiftKanbanTask(taskTitle, currentStatus, direction) {
    const note = notes.find(n => n.id === selectedNoteId);
    if (!note || !note.aiTasksJson) return;
    
    try {
        const tasks = JSON.parse(note.aiTasksJson);
        const taskIdx = tasks.findIndex(t => t.title === taskTitle && t.status === currentStatus);
        
        if (taskIdx !== -1) {
            const statuses = ["To Do", "In Progress", "Done"];
            const currIdx = statuses.indexOf(currentStatus);
            const targetIdx = currIdx + direction;
            
            if (targetIdx >= 0 && targetIdx < statuses.length) {
                tasks[taskIdx].status = statuses[targetIdx];
                note.aiTasksJson = JSON.stringify(tasks);
                saveToStorage();
                renderAiToolView(6); // Re-render boards
            }
        }
    } catch(e) {
        console.error("Failed to shift task status: ", e);
    }
}

// --- TOOL 9: CONVERSATIONAL CHAT MODULE ---

let chatMessagesHistory = {}; // selectedNoteId -> array of ChatMessage objects

function renderWorkspaceChat(container) {
    container.innerHTML = '';
    
    // Create Chat Layout
    const chatWrapper = document.createElement('div');
    chatWrapper.style.display = 'flex';
    chatWrapper.style.flexDirection = 'column';
    chatWrapper.style.height = '100%';
    chatWrapper.style.flexGrow = '1';
    
    const messagesContainer = document.createElement('div');
    messagesContainer.className = 'chat-messages-container';
    messagesContainer.id = 'chat-messages-box';
    
    const inputWrapper = document.createElement('div');
    inputWrapper.className = 'chat-input-wrapper';
    inputWrapper.innerHTML = `
        <textarea id="chat-input-field" placeholder="Ask details about this active note..."></textarea>
        <button id="chat-send-btn" class="btn btn-primary" style="padding: 10px 14px; border-radius: 8px;">
            <svg viewBox="0 0 24 24" class="small-icon"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" fill="currentColor"/></svg>
        </button>
    `;
    
    chatWrapper.appendChild(messagesContainer);
    chatWrapper.appendChild(inputWrapper);
    container.appendChild(chatWrapper);
    
    // Render Message stream
    const history = chatMessagesHistory[selectedNoteId] || [
        { sender: "ai", text: "Hello! I am your cognitive note Q&A assistant. Type queries to search, clarify, or expand concepts strictly scoped inside this note." }
    ];
    
    messagesContainer.innerHTML = '';
    history.forEach(msg => {
        const bubble = document.createElement('div');
        bubble.className = `chat-bubble ${msg.sender}`;
        
        let citationMark = '';
        if (msg.sender === "ai" && msg.citations && msg.citations.length > 0) {
            citationMark = `
                <div class="chat-citation-row">
                    <span>Citations:</span>
                    <span style="background-color: var(--border-color); padding: 1px 6px; border-radius: 4px; font-weight: 500;">${msg.citations[0]}</span>
                </div>
            `;
        }
        
        // simple paragraph breaker
        const formattedText = msg.text.replace(/\n/g, "<br>");
        bubble.innerHTML = `<div>${formattedText}</div>${citationMark}`;
        messagesContainer.appendChild(bubble);
    });
    
    // Scroll to bottom
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
    
    // Input action hooks
    const sendBtn = inputWrapper.querySelector('#chat-send-btn');
    const inputField = inputWrapper.querySelector('#chat-input-field');
    
    function send() {
        const text = inputField.value.trim();
        if (text === '') return;
        
        // Append user message
        const currentHist = chatMessagesHistory[selectedNoteId] || [
            { sender: "ai", text: "Hello! I am your cognitive note Q&A assistant. Type queries to search, clarify, or expand concepts strictly scoped inside this note." }
        ];
        
        currentHist.push({ sender: "user", text: text });
        chatMessagesHistory[selectedNoteId] = currentHist;
        
        inputField.value = '';
        renderWorkspaceChat(container);
        
        // Query Gemini Chat
        queryGeminiChatAssistant(text, messagesContainer);
    }
    
    sendBtn.onclick = send;
    inputField.onkeydown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            send();
        }
    };
}

async function queryGeminiChatAssistant(queryText, messagesBox) {
    const key = getGeminiApiKey();
    if (!key || key === '') {
        appendAiErrorMessage(messagesBox, "API Key is missing! Please configure GEMINI_API_KEY in Settings modal first.");
        return;
    }
    
    // Add loader bubble
    const loader = document.createElement('div');
    loader.className = 'chat-loading-bubble';
    loader.innerHTML = `<span class="chat-dot"></span><span class="chat-dot"></span><span class="chat-dot"></span>`;
    messagesBox.appendChild(loader);
    messagesBox.scrollTop = messagesBox.scrollHeight;
    
    const note = notes.find(n => n.id === selectedNoteId);
    let prompt = "";
    if (note) {
        prompt = `
            You are an intelligent Q&A chat assistant for "Smart Notes AI".
            Help the user answer their query about the active note.
            Active Note Title: "${note.title}"
            Active Note Content:
            "${note.content}"
            
            Other notes or tags context is: tags = "${note.tags}".
            
            Provide a polite, conversational, professional answer referencing the note. Be direct and clear. Keep the answer structured.
            
            Query: "${queryText}"
        `;
    } else {
        prompt = `
            You are an Q&A chat assistant for "Smart Notes AI".
            The user does not have an active note selected. Clarify politely that they can select a note to converse explicitly about its text, but still answer their question general-purpose or helpfully.
            
            Query: "${queryText}"
        `;
    }
    
    try {
        const responseText = await callGeminiAPI(key, prompt);
        
        // Remove loader
        loader.remove();
        
        const currentHist = chatMessagesHistory[selectedNoteId] || [];
        currentHist.push({
            sender: "ai",
            text: responseText,
            citations: note ? [note.title] : []
        });
        chatMessagesHistory[selectedNoteId] = currentHist;
        
        // Re-render
        renderAiToolView(9);
    } catch (e) {
        loader.remove();
        appendAiErrorMessage(messagesBox, "Error querying Gemini API: " + e.message);
    }
}

function appendAiErrorMessage(messagesBox, errText) {
    const currentHist = chatMessagesHistory[selectedNoteId] || [];
    currentHist.push({
        sender: "ai",
        text: "Error: " + errText
    });
    chatMessagesHistory[selectedNoteId] = currentHist;
    renderAiToolView(9);
}

// --- DIRECT CLIENT SIDE GEMINI API FETCH CONNECTOR ---

async function callGeminiAPI(apiKey, prompt) {
    // Standard fetch to v1beta of Generative Language APIs
    const model = "gemini-2.5-flash";
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
    
    const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            contents: [
                {
                    parts: [
                        { text: prompt }
                    ]
                }
            ],
            generationConfig: {
                temperature: 0.2
            }
        })
    });
    
    if (!response.ok) {
        const errJson = await response.json().catch(() => ({}));
        const errMsg = errJson.error?.message || response.statusText;
        throw new Error(`HTTP ${response.status}: ${errMsg}`);
    }
    
    const data = await response.json();
    const candidateText = data.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!candidateText) {
        throw new Error("Empty candidate content received from Gemini model.");
    }
    return candidateText;
}

async function runCognitiveAnalysis(toolNum, styleMode) {
    const key = getGeminiApiKey();
    const container = document.getElementById('ai-content-body');
    
    if (!key || key === '') {
        alert("API Key is missing! Click the 'Settings' toggle button (gear icon) in the top-right of the sidebar to configure your key.");
        return;
    }
    
    const note = notes.find(n => n.id === selectedNoteId);
    if (!note) return;
    
    if (!note.content || note.content.trim() === '') {
        alert("Your note is empty! Please write or draft some messy details first before running the AI analyzer.");
        return;
    }
    
    let prompt = "";
    let loadingMsg = "";
    
    switch (toolNum) {
        case 1: // Writing enhancer
            loadingMsg = `Refining text into beautiful ${styleMode} alignments...`;
            prompt = `
                You are a writing improvement expert. Your task is to transform the following raw note content into high-quality improved writing suited for the requested style: ${styleMode} (Formal, Professional, Friendly, Concise, Persuasive).
                
                Output original text and improved text inside a structured JSON. Be precise.
                
                Respond ONLY with a valid JSON in this exact structure:
                {
                  "original": "exactly original raw text",
                  "improved": "beautifully written improved version in ${styleMode} style",
                  "changesReason": "summary of vocabulary or syntax adjustments"
                }
                
                Raw notes content:
                "${note.content}"
            `;
            break;
            
        case 2: // Summary dashboard
            loadingMsg = "Analyzing dates, entity stakeholders, chronologies, and action matrices...";
            prompt = `
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
            `;
            break;
            
        case 3: // Concept tree
            loadingMsg = "Structuring brain dumps into multi-level JSON categories...";
            prompt = `
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
            `;
            break;
            
        case 4: // Mind map canvas
            loadingMsg = "Mapping core visual coordinates and hub connectors...";
            prompt = `
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
            `;
            break;
            
        case 5: // Execution roadmap
            loadingMsg = "Plotting delivery dates, milestones, objectives, and phases...";
            prompt = `
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
            `;
            break;
            
        case 6: // Task Board Kanban
            loadingMsg = "Extracting estimations, status categories, and priority boards...";
            prompt = `
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
            `;
            break;
            
        case 7: // Study Center Quiz & Flashcards
            loadingMsg = "Formulating quiz structures, options, 3D flashcards, and guide indices...";
            prompt = `
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
            `;
            break;
            
        case 8: // Decision matrix options
            loadingMsg = "Conducting SWOT, weighting alternatives, rating risks, and recommendation matrix...";
            prompt = `
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
            `;
            break;
    }
    
    renderLoadingState(container, loadingMsg);
    
    try {
        const rawResponse = await callGeminiAPI(key, prompt);
        
        // Clean JSON formatting boundaries returned by Google models
        let cleanedJson = rawResponse.trim();
        if (cleanedJson.startsWith("```json")) {
            cleanedJson = cleanedJson.replace(/^```json/, "");
        } else if (cleanedJson.startsWith("```")) {
            cleanedJson = cleanedJson.replace(/^```/, "");
        }
        if (cleanedJson.endsWith("```")) {
            cleanedJson = cleanedJson.replace(/```$/, "");
        }
        cleanedJson = cleanedJson.trim();
        
        // Confirm JSON validity
        let parsed = null;
        try {
            parsed = JSON.parse(cleanedJson);
        } catch (jsonErr) {
            // Special treatment for text improvement which could sometimes output text directly
            if (toolNum === 1) {
                parsed = { original: note.content, improved: rawResponse, changesReason: "Vocabulary, phrasing syntax alignments." };
                cleanedJson = JSON.stringify(parsed);
            } else {
                throw new Error("Gemini did not return a valid JSON structure: " + jsonErr.message + "\n\nRaw output received: " + rawResponse);
            }
        }
        
        // Save output to Note instance
        const cacheKey = getNoteCacheKey(toolNum);
        notes = notes.map(n => {
            if (n.id === selectedNoteId) {
                return {
                    ...n,
                    [cacheKey]: cleanedJson
                };
            }
            return n;
        });
        
        saveToStorage();
        renderToolOutput(toolNum, parsed, container);
    } catch (e) {
        renderErrorState(container, e.message, toolNum);
    }
}

// --- MODAL UTILITIES & NAVIGATION BINDINGS ---

function setupEventListeners() {
    // 1. New Folder trigger click
    document.getElementById('add-folder-btn').onclick = () => {
        document.getElementById('add-folder-modal').style.display = 'flex';
        document.getElementById('folder-name-input').value = '';
    };
    
    document.getElementById('cancel-folder-modal-btn').onclick = () => {
        document.getElementById('add-folder-modal').style.display = 'none';
    };
    
    document.getElementById('confirm-folder-modal-btn').onclick = () => {
        const name = document.getElementById('folder-name-input').value;
        const activeChoice = document.querySelector('.icon-choice.active');
        const icon = activeChoice ? activeChoice.getAttribute('data-icon') : 'Folder';
        
        createFolder(name, icon);
        document.getElementById('add-folder-modal').style.display = 'none';
    };
    
    // Icon selectors inside folder modal
    const choices = document.querySelectorAll('.icon-choice');
    choices.forEach(btn => {
        btn.onclick = () => {
            choices.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
        };
    });
    
    // 2. Settings key modal trigger click
    document.getElementById('settings-toggle-btn').onclick = () => {
        document.getElementById('settings-modal').style.display = 'flex';
        document.getElementById('api-key-input').value = getGeminiApiKey();
    };
    
    document.getElementById('cancel-settings-btn').onclick = () => {
        document.getElementById('settings-modal').style.display = 'none';
    };
    
    document.getElementById('save-settings-btn').onclick = () => {
        const key = document.getElementById('api-key-input').value.trim();
        saveGeminiApiKey(key);
        document.getElementById('settings-modal').style.display = 'none';
        alert("API Key configuration stored successfully in localStorage.");
    };
    
    // 3. New note triggers
    document.getElementById('new-note-btn').onclick = createNewNote;
    document.getElementById('empty-create-note-btn').onclick = createNewNote;
    
    // 4. Input auto-savers
    document.getElementById('note-title-field').onkeyup = triggerAutosave;
    document.getElementById('note-tags-field').onkeyup = triggerAutosave;
    document.getElementById('note-content-field').onkeyup = triggerAutosave;
    
    // 5. Search filtering
    document.getElementById('search-input').oninput = (e) => {
        searchQuery = e.target.value;
        renderNotes();
    };
    
    // 6. AI Toolbar tool selectors
    document.querySelectorAll('.ai-tool-btn').forEach(btn => {
        btn.onclick = () => {
            const toolNum = parseInt(btn.getAttribute('data-tool'));
            openAiTool(toolNum);
        };
    });
    
    document.getElementById('close-ai-pane-btn').onclick = closeAiTools;
    
    // 7. Mobile back btn
    document.getElementById('mobile-back-btn').onclick = () => {
        document.body.classList.remove('show-editor');
        document.body.classList.add('show-notes');
        syncMobileTabs();
    };
    
    // 8. Mobile navigation tab clicks
    document.getElementById('mobile-nav-notes').onclick = () => {
        document.body.className = 'show-notes';
        syncMobileTabs();
    };
    
    document.getElementById('mobile-nav-editor').onclick = () => {
        if (selectedNoteId === null) {
            alert("Please create or select a note from the list first!");
            return;
        }
        document.body.className = 'show-editor';
        syncMobileTabs();
    };
    
    document.getElementById('mobile-nav-ai').onclick = () => {
        if (selectedNoteId === null) {
            alert("Please create or select a note from the list first!");
            return;
        }
        if (activeAiTool === null) {
            openAiTool(2); // default to AI summary if none selected
        } else {
            document.body.className = 'show-ai';
            syncMobileTabs();
        }
    };
}

function syncMobileTabs() {
    const isNotes = document.body.classList.contains('show-notes') || (!document.body.classList.contains('show-editor') && !document.body.classList.contains('show-ai'));
    const isEditor = document.body.classList.contains('show-editor');
    const isAi = document.body.classList.contains('show-ai');
    
    document.getElementById('mobile-nav-notes').classList.toggle('active', isNotes);
    document.getElementById('mobile-nav-editor').classList.toggle('active', isEditor);
    document.getElementById('mobile-nav-ai').classList.toggle('active', isAi);
}
