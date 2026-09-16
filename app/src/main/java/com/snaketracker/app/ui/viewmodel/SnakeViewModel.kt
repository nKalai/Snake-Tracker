package com.snaketracker.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.entities.*
import com.snaketracker.app.ui.model.BackupExportState
import com.snaketracker.app.ui.model.BackupImportState
import com.snaketracker.app.ui.model.UpcomingEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SnakeViewModel(
    private val repository: Repository,
    private val backup: BackupCoordinator
) : ViewModel() {

    val snakes: Flow<List<Snake>> = repository.getAllSnakes()
    val foodStock: Flow<List<FoodStockItem>> = repository.getFoodStock()

    // Projects each reminder-enabled snake's feeding schedule (last feeding + interval,
    // repeated) out about 4 months, purely from locally stored data - used to populate
    // the calendar and the "upcoming/overdue" agenda list.
    val upcomingEvents: Flow<List<UpcomingEvent>> =
        combine(repository.getAllSnakes(), repository.getLastFeedingPerSnake()) { snakes, lastDates ->
            val lastBySnake = lastDates.associateBy { it.snakeId }
            val now = System.currentTimeMillis()
            val dayMillis = TimeUnit.DAYS.toMillis(1)
            val horizon = now + TimeUnit.DAYS.toMillis(120)

            val events = mutableListOf<UpcomingEvent>()
            for (snake in snakes) {
                if (!snake.remindersEnabled || snake.feedingIntervalDays <= 0) continue
                val intervalMillis = snake.feedingIntervalDays * dayMillis
                val lastDate = lastBySnake[snake.id]?.lastDate

                // If never fed, the first reminder is "due now"; otherwise it's one
                // interval after the last logged feeding.
                var nextDue = if (lastDate == null) now else lastDate + intervalMillis

                while (nextDue <= horizon) {
                    events.add(UpcomingEvent(snake.id, snake.name, nextDue, isOverdue = nextDue < now))
                    nextDue += intervalMillis
                }
            }
            events.sortedBy { it.dueDateMillis }
        }

    private val _backupExportState = MutableStateFlow<BackupExportState?>(null)

    /** Non-null while the Settings export-result dialog should be showing. */
    val backupExportState: StateFlow<BackupExportState?> = _backupExportState.asStateFlow()

    private var exportJob: Job? = null

    /**
     * Exports the database to the [destination] returned by the SAF
     * create-document picker; the coordinator runs both halves off the main
     * thread and decides the dialog state (issue #26).
     *
     * One export at a time: a call while an export is still running is
     * ignored, so a fast double-tap cannot race two results into the dialog.
     */
    fun exportBackup(destination: Uri) {
        if (exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            _backupExportState.value = backup.exportBackup(destination)
        }
    }

    /** Hides the export result dialog. */
    fun dismissBackupExport() {
        _backupExportState.value = null
    }

    private val _pendingImportUri = MutableStateFlow<Uri?>(null)

    /** The picked backup file while the "replace everything" confirmation dialog is showing. */
    val pendingImportUri: StateFlow<Uri?> = _pendingImportUri.asStateFlow()

    private val _backupImportState = MutableStateFlow<BackupImportState?>(null)

    /** Non-null while the Settings import-result dialog should be showing. */
    val backupImportState: StateFlow<BackupImportState?> = _backupImportState.asStateFlow()

    /**
     * Records the file picked by the Storage Access Framework picker so the
     * Settings screen can warn that importing replaces all current data.
     * Nothing is read or imported here: [confirmBackupImport] runs the
     * engine, [cancelBackupImport] drops the pick and leaves the database
     * untouched (issue #28 WB2).
     */
    fun requestBackupImport(picked: Uri) {
        _pendingImportUri.value = picked
    }

    /** Drops the pending pick; the engine never runs for it. */
    fun cancelBackupImport() {
        _pendingImportUri.value = null
    }

    /**
     * Runs the confirmed import through [BackupCoordinator.importBackup] -
     * read, engine, and the success re-arm all live there - and shows the
     * returned outcome (issue #28 WB3/WB4/WB5).
     */
    fun confirmBackupImport() {
        val source = _pendingImportUri.value ?: return
        _pendingImportUri.value = null
        viewModelScope.launch {
            _backupImportState.value = backup.importBackup(source)
        }
    }

    /** Hides the import result dialog. */
    fun dismissBackupImport() {
        _backupImportState.value = null
    }

    fun snake(id: Long): Flow<Snake?> = repository.getSnake(id)
    fun feedingEvents(snakeId: Long): Flow<List<FeedingEvent>> = repository.getFeedingEvents(snakeId)
    fun shedEvents(snakeId: Long): Flow<List<ShedEvent>> = repository.getShedEvents(snakeId)
    fun weightEntries(snakeId: Long): Flow<List<WeightEntry>> = repository.getWeightEntries(snakeId)

    fun addSnake(snake: Snake) = viewModelScope.launch { repository.addSnake(snake) }
    fun updateSnake(snake: Snake) = viewModelScope.launch { repository.updateSnake(snake) }
    fun deleteSnake(snake: Snake) = viewModelScope.launch { repository.deleteSnake(snake) }

    // Logs a feeding; if it references a food-stock item, that item's quantity is
    // decremented by one in the same local database transaction.
    fun logFeeding(event: FeedingEvent) = viewModelScope.launch { repository.logFeedingConsumingStock(event) }
    fun deleteFeeding(event: FeedingEvent) = viewModelScope.launch { repository.deleteFeeding(event) }

    fun addShed(event: ShedEvent) = viewModelScope.launch { repository.addShed(event) }
    fun deleteShed(event: ShedEvent) = viewModelScope.launch { repository.deleteShed(event) }

    fun addWeight(entry: WeightEntry) = viewModelScope.launch { repository.addWeight(entry) }
    fun deleteWeight(entry: WeightEntry) = viewModelScope.launch { repository.deleteWeight(entry) }

    fun addFoodStock(item: FoodStockItem) = viewModelScope.launch { repository.addFoodStock(item) }
    fun updateFoodStock(item: FoodStockItem) = viewModelScope.launch { repository.updateFoodStock(item) }
    fun deleteFoodStock(item: FoodStockItem) = viewModelScope.launch { repository.deleteFoodStock(item) }
}
