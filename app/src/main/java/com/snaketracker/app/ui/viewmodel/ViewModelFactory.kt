package com.snaketracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupJsonSource

class ViewModelFactory(
    private val repository: Repository,
    private val backupJsonSource: BackupJsonSource,
    private val backupExportGateway: BackupExportGateway
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnakeViewModel::class.java)) {
            return SnakeViewModel(repository, backupJsonSource, backupExportGateway) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
