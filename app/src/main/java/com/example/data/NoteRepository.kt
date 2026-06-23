package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allFolders: Flow<List<Folder>> = noteDao.getAllFolders()
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    fun getNotesInFolder(folderId: Int): Flow<List<Note>> = noteDao.getNotesInFolder(folderId)

    suspend fun getNoteById(id: Int): Note? = noteDao.getNoteById(id)

    suspend fun insertFolder(folder: Folder): Long = noteDao.insertFolder(folder)

    suspend fun deleteFolder(folder: Folder) = noteDao.deleteFolder(folder)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)
}
