package com.snaketracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snaketracker.app.ui.DateUtils
import com.snaketracker.app.ui.viewmodel.SnakeViewModel

private enum class DetailTab { FEEDING, SHED, WEIGHT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnakeDetailScreen(
    viewModel: SnakeViewModel,
    snakeId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val snake by viewModel.snake(snakeId).collectAsStateWithLifecycle(initialValue = null)
    var tab by remember { mutableStateOf(DetailTab.FEEDING) }
    var showAddDialog by remember { mutableStateOf(false) }

    val feedingEvents by viewModel.feedingEvents(snakeId).collectAsStateWithLifecycle(initialValue = emptyList())
    val shedEvents by viewModel.shedEvents(snakeId).collectAsStateWithLifecycle(initialValue = emptyList())
    val weightEntries by viewModel.weightEntries(snakeId).collectAsStateWithLifecycle(initialValue = emptyList())
    val foodStock by viewModel.foodStock.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(snake?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add entry")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            snake?.let { s ->
                if (s.species.isNotBlank() || s.morph.isNotBlank()) {
                    Text(
                        listOf(s.species, s.morph).filter { it.isNotBlank() }.joinToString(" · "),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                val lastFeeding = feedingEvents.firstOrNull()
                if (s.remindersEnabled) {
                    val nextDueText = if (lastFeeding != null) {
                        val nextDue = lastFeeding.date + s.feedingIntervalDays * 24L * 60 * 60 * 1000
                        val daysLeft = (nextDue - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
                        if (daysLeft <= 0) "Feeding due now" else "Next feeding due in $daysLeft day(s)"
                    } else "No feedings logged yet"
                    Text(
                        nextDueText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(selected = tab == DetailTab.FEEDING, onClick = { tab = DetailTab.FEEDING }, text = { Text("Feeding") })
                Tab(selected = tab == DetailTab.SHED, onClick = { tab = DetailTab.SHED }, text = { Text("Shed") })
                Tab(selected = tab == DetailTab.WEIGHT, onClick = { tab = DetailTab.WEIGHT }, text = { Text("Weight") })
            }

            when (tab) {
                DetailTab.FEEDING -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(feedingEvents, key = { it.id }) { event ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(DateUtils.format(event.date), style = MaterialTheme.typography.titleSmall)
                                    val statusText = when {
                                        !event.accepted -> "Refused"
                                        event.assist -> "Assist-fed"
                                        else -> "Accepted"
                                    }
                                    Text("${event.foodType} ${event.foodSize} — $statusText")
                                    if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { viewModel.deleteFeeding(event) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
                DetailTab.SHED -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shedEvents, key = { it.id }) { event ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(DateUtils.format(event.date), style = MaterialTheme.typography.titleSmall)
                                    Text(if (event.complete) "Complete shed" else "Incomplete shed")
                                    if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { viewModel.deleteShed(event) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
                DetailTab.WEIGHT -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(weightEntries, key = { it.id }) { entry ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(DateUtils.format(entry.date), style = MaterialTheme.typography.titleSmall)
                                    Text("${entry.grams} g")
                                    if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { viewModel.deleteWeight(entry) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        when (tab) {
            DetailTab.FEEDING -> AddFeedingDialog(
                snakeId = snakeId,
                stockItems = foodStock,
                onDismiss = { showAddDialog = false },
                onSave = { viewModel.logFeeding(it); showAddDialog = false }
            )
            DetailTab.SHED -> AddShedDialog(
                snakeId = snakeId,
                onDismiss = { showAddDialog = false },
                onSave = { viewModel.addShed(it); showAddDialog = false }
            )
            DetailTab.WEIGHT -> AddWeightDialog(
                snakeId = snakeId,
                onDismiss = { showAddDialog = false },
                onSave = { viewModel.addWeight(it); showAddDialog = false }
            )
        }
    }
}
