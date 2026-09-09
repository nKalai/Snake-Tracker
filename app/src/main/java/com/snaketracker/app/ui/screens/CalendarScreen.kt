package com.snaketracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snaketracker.app.ui.CalendarUtils
import com.snaketracker.app.ui.DateUtils
import com.snaketracker.app.ui.model.UpcomingEvent
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: SnakeViewModel,
    onSnakeClick: (Long) -> Unit
) {
    val events by viewModel.upcomingEvents.collectAsStateWithLifecycle(initialValue = emptyList())

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val zone = ZoneId.systemDefault()
    val eventsByDate = remember(events) {
        events.groupBy { Instant.ofEpochMilli(it.dueDateMillis).atZone(zone).toLocalDate() }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Snake Tracker") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    "${currentMonth.month.getDisplayName(java.time.format.TextStyle.FULL, LocalLocale.current.platformLocale)} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                }
            }

            // Weekday headers
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                CalendarUtils.weekdayLabels().forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Day grid
            val cells = CalendarUtils.daysGridFor(currentMonth)
            val today = LocalDate.now()
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date != null && date == selectedDate,
                                hasEvent = date != null && eventsByDate.containsKey(date),
                                hasOverdue = date != null && eventsByDate[date]?.any { it.isOverdue } == true,
                                onClick = { date?.let { selectedDate = if (selectedDate == it) null else it } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Agenda: either the selected day's events, or the overall upcoming/overdue list
            val agendaTitle: String
            val agendaEvents: List<UpcomingEvent>
            if (selectedDate != null) {
                agendaTitle = "On ${DateUtils.format(selectedDate!!.atStartOfDay(zone).toInstant().toEpochMilli())}"
                agendaEvents = eventsByDate[selectedDate] ?: emptyList()
            } else {
                agendaTitle = "Upcoming & overdue feedings"
                agendaEvents = events.take(20)
            }

            Text(
                agendaTitle,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (agendaEvents.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedDate != null) "Nothing due this day." else "No feedings scheduled yet. Add a snake and log a feeding to get started.",
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(agendaEvents, key = { "${it.snakeId}-${it.dueDateMillis}" }) { event ->
                        ElevatedCard(onClick = { onSnakeClick(event.snakeId) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(event.snakeName, style = MaterialTheme.typography.titleSmall)
                                    Text(DateUtils.format(event.dueDateMillis), style = MaterialTheme.typography.bodySmall)
                                }
                                if (event.isOverdue) {
                                    AssistChip(onClick = {}, label = { Text("Overdue") })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvent: Boolean,
    hasOverdue: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                if (hasEvent) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasOverdue) MaterialTheme.colorScheme.error
                                else if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }
        }
    }
}
