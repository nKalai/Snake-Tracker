package com.snaketracker.app.ui.viewmodel

import com.snaketracker.app.ui.model.BackupImportState

// The import-behavior tests build the ViewModel over the shared JVM fixture
// (com.snaketracker.app.data.fakeTestRepository); this file holds only the
// viewmodel-local test helpers.

internal fun backupImportStateOf(viewModel: SnakeViewModel): BackupImportState? =
    viewModel.backupImportState.value
