package com.snaketracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.snaketracker.app.data.Repository

/**
 * Builds the [SnakeViewModel] from the two collaborators the app wires: the
 * CRUD [Repository] and the single [BackupCoordinator]. The backup port
 * clump this factory used to mirror (four ports plus a re-arm lambda) is
 * gone - it lives behind the coordinator now (PR #34 review 🔴).
 */
class ViewModelFactory(
    private val repository: Repository,
    private val backup: BackupCoordinator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnakeViewModel::class.java)) {
            return SnakeViewModel(repository = repository, backup = backup) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
