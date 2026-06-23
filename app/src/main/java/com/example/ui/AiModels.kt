package com.example.ui

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Text Improvement
data class TextImprovementResult(
    val original: String,
    val improved: String,
    val changesReason: String
) {
    companion object {
        fun fromJson(jsonStr: String, textFallbackOriginal: String): TextImprovementResult {
            return try {
                val json = JSONObject(jsonStr)
                TextImprovementResult(
                    original = json.optString("original", textFallbackOriginal),
                    improved = json.optString("improved", textFallbackOriginal),
                    changesReason = json.optString("changesReason", "Writing styled clean and improved formatting.")
                )
            } catch (e: Exception) {
                TextImprovementResult(
                    original = textFallbackOriginal,
                    improved = jsonStr, // fallback if plain text returned
                    changesReason = "Simplified style enhancements structure."
                )
            }
        }
    }
}

// AI Summary
data class SummaryResult(
    val summary: String,
    val keyPoints: List<String>,
    val importantDates: List<String>,
    val importantNames: List<String>,
    val actionItems: List<String>
) {
    companion object {
        fun fromJson(jsonStr: String): SummaryResult {
            return try {
                val json = JSONObject(jsonStr)
                SummaryResult(
                    summary = json.optString("summary", "No summary text generated."),
                    keyPoints = json.optJSONArray("keyPoints")?.toList() ?: emptyList(),
                    importantDates = json.optJSONArray("importantDates")?.toList() ?: emptyList(),
                    importantNames = json.optJSONArray("importantNames")?.toList() ?: emptyList(),
                    actionItems = json.optJSONArray("actionItems")?.toList() ?: emptyList()
                )
            } catch (e: Exception) {
                SummaryResult(
                    summary = jsonStr,
                    keyPoints = listOf("Extract key points: Read detailed note content"),
                    importantDates = emptyList(),
                    importantNames = emptyList(),
                    actionItems = emptyList()
                )
            }
        }
    }
}

// Idea Organizer (Tree hierarchies)
data class IdeaNode(
    val title: String,
    val children: List<IdeaNode> = emptyList()
) {
    companion object {
        fun fromJson(jsonStr: String): IdeaNode {
            return try {
                val json = JSONObject(jsonStr)
                parseNode(json)
            } catch (e: Exception) {
                // Return default fallback hierarchy on failure
                IdeaNode(
                    title = "Messy Raw Thoughts",
                    children = listOf(
                        IdeaNode("Core Ideas", listOf(IdeaNode("Expand notes content for further details"))),
                        IdeaNode("Actionable thoughts")
                    )
                )
            }
        }

        private fun parseNode(obj: JSONObject): IdeaNode {
            val title = obj.optString("title", "Topic")
            val childrenArray = obj.optJSONArray("children")
            val childrenList = mutableListOf<IdeaNode>()
            if (childrenArray != null) {
                for (i in 0 until childrenArray.length()) {
                    val childObj = childrenArray.optJSONObject(i)
                    if (childObj != null) {
                        childrenList.add(parseNode(childObj))
                    } else {
                        val childStr = childrenArray.optString(i)
                        if (childStr.isNotEmpty()) {
                            childrenList.add(IdeaNode(childStr))
                        }
                    }
                }
            }
            return IdeaNode(title, childrenList)
        }
    }
}

// Mind Map Result
data class MindMapNode(
    val id: String,
    val label: String,
    val category: String, // central, parent, child
    val color: String
)

data class MindMapEdge(
    val from: String,
    val to: String
)

data class MindMapResult(
    val nodes: List<MindMapNode>,
    val edges: List<MindMapEdge>
) {
    companion object {
        fun fromJson(jsonStr: String): MindMapResult {
            return try {
                val json = JSONObject(jsonStr)
                val nodesArray = json.optJSONArray("nodes")
                val edgesArray = json.optJSONArray("edges")
                
                val nodes = mutableListOf<MindMapNode>()
                if (nodesArray != null) {
                    for (i in 0 until nodesArray.length()) {
                        val obj = nodesArray.getJSONObject(i)
                        nodes.add(
                            MindMapNode(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                label = obj.optString("label", "Node"),
                                category = obj.optString("category", "child"),
                                color = obj.optString("color", "#2563EB")
                            )
                        )
                    }
                }
                
                val edges = mutableListOf<MindMapEdge>()
                if (edgesArray != null) {
                    for (i in 0 until edgesArray.length()) {
                        val obj = edgesArray.getJSONObject(i)
                        edges.add(
                            MindMapEdge(
                                from = obj.getString("from"),
                                to = obj.getString("to")
                            )
                        )
                    }
                }
                MindMapResult(nodes, edges)
            } catch (e: Exception) {
                // Fallback default structure
                val nodes = listOf(
                    MindMapNode("1", "Central Element", "central", "#EF4444"),
                    MindMapNode("2", "Subtopic A", "parent", "#F59E0B"),
                    MindMapNode("3", "Subtopic B", "parent", "#10B981"),
                    MindMapNode("4", "Detail A1", "child", "#3B82F6"),
                    MindMapNode("5", "Detail B1", "child", "#8B5CF6")
                )
                val edges = listOf(
                    MindMapEdge("1", "2"),
                    MindMapEdge("1", "3"),
                    MindMapEdge("2", "4"),
                    MindMapEdge("3", "5")
                )
                MindMapResult(nodes, edges)
            }
        }
    }
}

// Action Plan Roadmap
data class ActionPhase(
    val phase: String,
    val objectives: List<String>,
    val deliverables: List<String>,
    val duration: String
) {
    companion object {
        fun fromJsonList(jsonStr: String): List<ActionPhase> {
            return try {
                val array = if (jsonStr.trim().startsWith("[")) JSONArray(jsonStr) else JSONArray()
                val phases = mutableListOf<ActionPhase>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    phases.add(
                        ActionPhase(
                            phase = obj.optString("phase", "Phase ${i + 1}"),
                            objectives = obj.optJSONArray("objectives")?.toList() ?: emptyList(),
                            deliverables = obj.optJSONArray("deliverables")?.toList() ?: emptyList(),
                            duration = obj.optString("duration", "TBD")
                        )
                    )
                }
                if (phases.isEmpty()) throw Exception("Empty roadmap parsed")
                phases
            } catch (e: Exception) {
                listOf(
                    ActionPhase("Phase 1: Foundations", listOf("Define core purpose"), listOf("Requirements brief"), "1 week"),
                    ActionPhase("Phase 2: Execution", listOf("Build functional elements"), listOf("Alpha build release"), "2 weeks"),
                    ActionPhase("Phase 3: Launch", listOf("Audit features and polish"), listOf("Production deployment"), "1 week")
                )
            }
        }
    }
}

// Task Extraction Kanban
data class KanbanTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: String, // High, Medium, Low
    val estimatedTime: String,
    val dueDate: String,
    val status: String // To Do, In Progress, Done
) {
    companion object {
        fun fromJsonList(jsonStr: String): List<KanbanTask> {
            return try {
                val array = if (jsonStr.trim().startsWith("[")) JSONArray(jsonStr) else JSONArray()
                val tasks = mutableListOf<KanbanTask>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    tasks.add(
                        KanbanTask(
                            id = UUID.randomUUID().toString(),
                            title = obj.optString("title", "Extract Note Task"),
                            priority = obj.optString("priority", "Medium"),
                            estimatedTime = obj.optString("estimatedTime", "15m"),
                            dueDate = obj.optString("dueDate", "Today"),
                            status = obj.optString("status", "To Do")
                        )
                    )
                }
                if (tasks.isEmpty()) throw Exception("Empty tasks parsed")
                tasks
            } catch (e: Exception) {
                listOf(
                    KanbanTask(title = "Read fully through raw notes", priority = "High", estimatedTime = "10m", dueDate = "Today", status = "To Do"),
                    KanbanTask(title = "Organize findings into categories", priority = "Medium", estimatedTime = "30m", dueDate = "Tomorrow", status = "To Do"),
                    KanbanTask(title = "Define execution next actions", priority = "High", estimatedTime = "20m", dueDate = "Today", status = "In Progress")
                )
            }
        }
    }
}

// Study Sheet (Quiz/Flashcards)
data class Flashcard(val front: String, val back: String)
data class QuizQuestion(val question: String, val options: List<String>, val correctIndex: Int)
data class KeyConcept(val name: String, val description: String)

data class StudyResult(
    val summary: String,
    val flashcards: List<Flashcard>,
    val quiz: List<QuizQuestion>,
    val concepts: List<KeyConcept>
) {
    companion object {
        fun fromJson(jsonStr: String): StudyResult {
            return try {
                val json = JSONObject(jsonStr)
                val summary = json.optString("summary", "Notes Study Overview")
                
                val flashcardsArray = json.optJSONArray("flashcards")
                val flashcards = mutableListOf<Flashcard>()
                if (flashcardsArray != null) {
                    for (i in 0 until flashcardsArray.length()) {
                        val obj = flashcardsArray.getJSONObject(i)
                        flashcards.add(Flashcard(obj.optString("front"), obj.optString("back")))
                    }
                }
                
                val quizArray = json.optJSONArray("quiz")
                val quiz = mutableListOf<QuizQuestion>()
                if (quizArray != null) {
                    for (i in 0 until quizArray.length()) {
                        val obj = quizArray.getJSONObject(i)
                        val optionsArr = obj.optJSONArray("options")
                        val options = mutableListOf<String>()
                        if (optionsArr != null) {
                            for (j in 0 until optionsArr.length()) {
                                options.add(optionsArr.getString(j))
                            }
                        }
                        quiz.add(QuizQuestion(obj.optString("question"), options, obj.optInt("correctIndex", 0)))
                    }
                }
                
                val conceptsArray = json.optJSONArray("concepts")
                val concepts = mutableListOf<KeyConcept>()
                if (conceptsArray != null) {
                    for (i in 0 until conceptsArray.length()) {
                        val obj = conceptsArray.getJSONObject(i)
                        concepts.add(KeyConcept(obj.optString("name"), obj.optString("description")))
                    }
                }
                
                StudyResult(summary, flashcards, quiz, concepts)
            } catch (e: Exception) {
                StudyResult(
                    summary = "No study material structured yet. Try generating again.",
                    flashcards = listOf(
                        Flashcard("Note-taking", "The practice of writing down pieces of information from a source."),
                        Flashcard("Gemini", "Google's powerful generative AI multimodal engine.")
                    ),
                    quiz = listOf(
                        QuizQuestion("What model is used for Smart Notes?", listOf("Gemini 3.5 Flash", "GPT-3", "Claude", "Ollama"), 0)
                    ),
                    concepts = listOf(
                        KeyConcept("Active recall", "Testing yourself on information retrieval to prompt deeper retention.")
                    )
                )
            }
        }
    }
}

// Decision Analyzer Matrix
data class DecisionOption(
    val option: String,
    val pros: List<String>,
    val cons: List<String>,
    val risks: List<String>,
    val score: Int, // 1 to 10
    val recommendation: String
) {
    companion object {
        fun fromJsonList(jsonStr: String): List<DecisionOption> {
            return try {
                val array = if (jsonStr.trim().startsWith("[")) JSONArray(jsonStr) else JSONArray()
                val options = mutableListOf<DecisionOption>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    options.add(
                        DecisionOption(
                            option = obj.optString("option", "Option ${i + 1}"),
                            pros = obj.optJSONArray("pros")?.toList() ?: emptyList(),
                            cons = obj.optJSONArray("cons")?.toList() ?: emptyList(),
                            risks = obj.optJSONArray("risks")?.toList() ?: emptyList(),
                            score = obj.optInt("score", 7),
                            recommendation = obj.optString("recommendation", "N/A")
                        )
                    )
                }
                if (options.isEmpty()) throw Exception("Empty options list")
                options
            } catch (e: Exception) {
                listOf(
                    DecisionOption("Option 1: Act immediately", listOf("First mover advantage"), listOf("Less planning"), listOf("High uncertainty"), 7, "Good choice for urgent tasks"),
                    DecisionOption("Option 2: Plan carefully", listOf("Risk aversion", "High quality"), listOf("Delayed launch"), listOf("Competitor speed"), 8, "Best choice for longevity success")
                )
            }
        }
    }
}

// Helper to convert JSONArray to List of Strings
fun JSONArray.toList(): List<String> {
    val list = mutableListOf<String>()
    for (i in 0 until this.length()) {
        list.add(this.getString(i))
    }
    return list
}
