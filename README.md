# Smart Notes AI - Single-Page Web Application

Smart Notes AI is a clean, minimalist, highly responsive single-page cognitive workspace. It converts messy brainstorming thoughts and outlines into highly structured visual assets—including interactive canvas mind maps, timeline roadmaps, hierarchical trees, study guides with 3D flashcards, multi-choice quizzes, comparative decision tables, and Kanban task boards.

This workspace operates **offline-first** using `localStorage` for complete data persistence (no Python or database backend required) and queries the Google Gemini API directly from the client.

---

## 📂 File Structure for Replit

To copy-paste and run this project immediately inside Replit, use the following structure:

```text
.
├── index.html   # Main application structure, layouts, modals, SVGs, and mobile tabs
├── style.css    # Material 3 typography, Cosmic Dark styling, transitions, and responsive cards
└── app.js       # Core storage state engine, dynamic UI drawers, interactive canvas, and Gemini API bindings
```

*These files are located in your Android assets directory: `/app/src/main/assets/`*

---

## 🚀 How to Run on Replit

1. Create an HTML/CSS/JS repl on [Replit](https://replit.com).
2. Create the three core files (`index.html`, `style.css`, and `app.js`) in the root workspace.
3. Copy-paste the corresponding contents of each file from `/app/src/main/assets/`.
4. Click **Run**.
5. Click the gear icon (**Settings**) in the top-right of your sidebar and input your **Gemini API Key**.
6. That's it! Your cognitive notes app is fully interactive, responsive, and persistent.

---

## 📱 Standalone Android App wrapping (WebView)

Our Java/Kotlin codebase has been fully rewritten into a high-performance programmatic WebView layout (`MainActivity.kt`). 
- **DOM Storage Enabled**: Fully supports `localStorage` persistence between app restarts.
- **Auto API-Key Integration**: Seamlessly reads your preconfigured `BuildConfig.GEMINI_API_KEY` (configured in the AI Studio secrets tab) and injects it securely on webview load as `window.GEMINI_API_KEY`—ensuring immediate AI functionality without manual key inputs!
- **System Back-button Dispatching**: Binds to the device back button to navigate backwards in the note history rather than exiting.

---

## 🧠 Breakdown of the 9 Cognitive AI Tools

1. **AI Writing Enhancer**: Refines text drafts into *Formal*, *Professional*, *Friendly*, *Concise*, or *Persuasive* styles. Highlights revisions with reasons and applies them with a tap.
2. **Dashboard Summary**: Generates a central synopsis alongside bulleted core takeaways, chronological due dates, stakeholder lists, and interactive action checklist items.
3. **Cognitive Concept Tree**: Maps unorganized text dumps into clean, nested, expandable hierarchical trees.
4. **Knowledge Graph Map**: Parses central themes into visual nodes and edges, drawing a custom network graph using an interactive HTML5 `<canvas>` that supports drag-and-drop.
5. **Operational Roadmap**: Projects stage-by-stage Gantt objectives, deliverables, and estimated duration blocks.
6. **Kanban Boards Extractor**: Sorts tasks into interactive *To Do*, *In Progress*, and *Done* cards with custom priority indicators. Drag and move cards directly.
7. **Learn Labs Study Center**: Compiles custom study plans complete with key terminology guides, interactive 3D rotating flashcards, and scored multi-choice quizzes.
8. **Decision Matrix**: Constructs swot columns scoring alternative pathways from 1 to 10 with detailed recommendation logs.
9. **Workspace Assistant**: Launches an interactive chat box scoped explicitly to the context of the active note for immediate Q&A retrieval.
