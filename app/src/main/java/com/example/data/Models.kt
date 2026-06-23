package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val iconName: String = "Folder"
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val folderId: Int? = null,
    val tags: String = "", // Comma-separated strings
    val lastModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    
    // Cached response fields for the tools
    val aiSummaryJson: String? = null,
    val aiTasksJson: String? = null,
    val aiActionPlanJson: String? = null,
    val aiMindMapJson: String? = null,
    val aiIdeaJson: String? = null,
    val aiStudyJson: String? = null,
    val aiDecisionJson: String? = null,
    val aiImprovedText: String? = null
)
