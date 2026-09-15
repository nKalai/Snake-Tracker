package com.snaketracker.app

import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupEngine
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.ui.viewmodel.BackupCoordinator
import com.snaketracker.app.ui.viewmodel.DefaultBackupCoordinator
import com.snaketracker.app.ui.viewmodel.ViewModelFactory

/**
 * The composition wiring as pure functions (PR #34 review, suggested
 * edge-case test 4): the production seam of the feature - which objects
 * serve the ViewModel - is one tested function instead of inline
 * composition inside the Activity. [SnakeTrackerApp] wires one
 * [BackupEngine] (the production implementation is `BackupRepository`), one
 * export gateway and one import gateway into a single [BackupCoordinator];
 * the same coordinator then serves every backup dialog.
 *
 * [rearmReminders] is the reschedule-only reminder entry (issue #28 WB5);
 * its off-main scheduling is the coordinator's policy, not this root's
 * (PR #34 review 🟢).
 */
fun createBackupCoordinator(
    backupEngine: BackupEngine,
    exportGateway: BackupExportGateway,
    importGateway: BackupImportGateway,
    rearmReminders: suspend () -> Unit
): BackupCoordinator = DefaultBackupCoordinator(
    backupEngine = backupEngine,
    exportGateway = exportGateway,
    importGateway = importGateway,
    rearmReminders = rearmReminders
)

/** Wraps the collaborators the [SnakeViewModel][com.snaketracker.app.ui.viewmodel.SnakeViewModel] is built from. */
fun createViewModelFactory(
    repository: Repository,
    backup: BackupCoordinator
): ViewModelFactory = ViewModelFactory(repository = repository, backup = backup)
