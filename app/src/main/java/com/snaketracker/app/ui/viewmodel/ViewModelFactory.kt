package com.snaketracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupJsonSink
import com.snaketracker.app.data.backup.BackupJsonSource

class ViewModelFactory(
    private val repository: Repository,
    private val backupJsonSource: BackupJsonSource,
    private val backupJsonSink: BackupJsonSink,
    private val backupExportGateway: BackupExportGateway,
    private val backupImportGateway: BackupImportGateway,
    private val rearmReminders: suspend () -> Unit
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnakeViewModel::class.java)) {
            return SnakeViewModel(
                repository = repository,
                backupJsonSource = backupJsonSource,
                backupJsonSink = backupJsonSink,
                backupExportGateway = backupExportGateway,
                backupImportGateway = backupImportGateway,
                rearmReminders = rearmReminders
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
