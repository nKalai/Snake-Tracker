package com.snaketracker.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snaketracker.app.R
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupJsonSink
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.data.entities.*
import com.snaketracker.app.ui.model.BackupImportState
import com.snaketracker.app.ui.model.UpcomingEvent
import com.snaketracker.app.ui.model.backupImportMessageFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SnakeViewModel(
    private val repository: Repository,
    private val backupJsonSink: BackupJsonSink,
    private val backupImportGateway: BackupImportGateway,
    private val rearmReminders: suspend () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
     * Runs the confirmed import: the picked file's bytes are read through
     * the gateway and passed to the backup sink — both the gateway's stream
     * work and the engine call (which fully parses the file before it
     * touches Room) on [ioDispatcher], never on the Main the confirm click
     * launched on (issue #28 WB3). The sink keeps the existing
     * replace-everything validation and transaction semantics unchanged -
     * any rejection leaves the device untouched. The outcome lands in
     * [backupImportState], and a success immediately re-arms the feeding
     * reminder through the shared reschedule entry point, so the alarm
     * follows the new data without waiting for the next app launch
     * (issue #28 WB5).
     */
    fun confirmBackupImport() {
        val source = _pendingImportUri.value ?: return
        _pendingImportUri.value = null
        viewModelScope.launch {
            val state = performImport(source)
            _backupImportState.value = state
            if (state is BackupImportState.Success) rearmAfterImport()
        }
    }

    /** Hides the import result dialog. */
    fun dismissBackupImport() {
        _backupImportState.value = null
    }

    private suspend fun performImport(source: Uri): BackupImportState =
        recoveringFrom(
            onFailure = { BackupImportState.Failure(R.string.backup_import_failure_unreadable) }
        ) {
            // Only a failed read can stop the engine from running, so the
            // engine's own failure mapping stays nested inside this read gate.
            val json = backupImportGateway.read(source)
            recoveringFrom(
                // A validated file that still fails mid-insert arrives as an
                // exception, not a typed rejection (see BackupRepository.importJson);
                // Room has already rolled the transaction back.
                onFailure = { BackupImportState.Failure(R.string.backup_import_failure_database) }
            ) {
                // The engine parses the whole file before Room dispatches its
                // transaction, so the call itself must leave Main (issue #28 WB3).
                when (val summary = withContext(ioDispatcher) { backupJsonSink.importJson(json) }) {
                    is ImportSummary.Success -> BackupImportState.Success(
                        BackupImportState.Counts(
                            snakes = summary.snakes,
                            feedings = summary.feedings,
                            sheds = summary.sheds,
                            weights = summary.weights,
                            foodStock = summary.foodStock
                        )
                    )
                    is ImportSummary.Failure ->
                        BackupImportState.Failure(backupImportMessageFor(summary.reason))
                }
            }
        }

    private suspend fun rearmAfterImport() {
        recoveringFrom(
            // The import itself completed; the receivers (alarm fire, boot,
            // permission re-grant) re-arm on their next event regardless.
            onFailure = { e -> Log.e(LOG_TAG, "Reminder re-arm after import failed", e) }
        ) {
            rearmReminders()
        }
    }

    /**
     * Runs [block], recovering from any failure through [onFailure] — the one
     * cancellation-safe catch idiom behind every import dialog path: a
     * [CancellationException] (scope death, navigation away) always
     * propagates instead of being reported as an import outcome.
     */
    private inline fun <T> recoveringFrom(
        onFailure: (Exception) -> T,
        block: () -> T
    ): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
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

    private companion object {
        const val LOG_TAG = "SnakeViewModel"
    }
}
