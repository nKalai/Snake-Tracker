package com.snaketracker.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupJsonSource
import com.snaketracker.app.data.entities.*
import com.snaketracker.app.ui.model.BackupExportState
import com.snaketracker.app.ui.model.UpcomingEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SnakeViewModel(
    private val repository: Repository,
    private val backupJsonSource: BackupJsonSource,
    private val backupExportGateway: BackupExportGateway
) : ViewModel() {

    // Cold database-backed flows, held lazily so the ViewModel can be
    // constructed in JVM tests (backup export state) without a Room database.
    val snakes: Flow<List<Snake>> by lazy { repository.getAllSnakes() }
    val foodStock: Flow<List<FoodStockItem>> by lazy { repository.getFoodStock() }

    // Projects each reminder-enabled snake's feeding schedule (last feeding + interval,
    // repeated) out about 4 months, purely from locally stored data - used to populate
    // the calendar and the "upcoming/overdue" agenda list.
    val upcomingEvents: Flow<List<UpcomingEvent>> by lazy {
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
    }

    private val _backupExportState = MutableStateFlow<BackupExportState?>(null)

    /** Non-null while the Settings export-result dialog should be showing. */
    val backupExportState: StateFlow<BackupExportState?> = _backupExportState.asStateFlow()

    /**
     * Dumps the database and writes it to the [destination] returned by the
     * SAF create-document picker. The gateway performs its stream work on
     * Dispatchers.IO; the Room snapshot reads leave the main thread via
     * Room's own executors. The outcome lands in [backupExportState].
     */
    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            _backupExportState.value = try {
                BackupExportState.Success(
                    backupExportGateway.save(destination, backupJsonSource.exportAll())
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupExportState.Failure(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Hides the export result dialog. */
    fun dismissBackupExport() {
        _backupExportState.value = null
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
