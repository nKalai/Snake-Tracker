package com.snaketracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupJsonSink

class ViewModelFactory(
    private val repository: Repository,
    private val backupJsonSink: BackupJsonSink,
    private val backupImportGateway: BackupImportGateway,
    private val rearmReminders: suspend () -> Unit
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnakeViewModel::class.java)) {
            return SnakeViewModel(
                repository,
                backupJsonSink,
                backupImportGateway,
                rearmReminders
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
