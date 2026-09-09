package com.snaketracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.ui.viewmodel.SnakeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnakeListScreen(
    viewModel: SnakeViewModel,
    onSnakeClick: (Long) -> Unit,
    onAddSnakeClick: () -> Unit
) {
    val snakes by viewModel.snakes.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Snakes", modifier = Modifier.testTag("snake_list_title")) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSnakeClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add snake")
            }
        }
    ) { padding ->
        if (snakes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No snakes yet. Tap + to add your first one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(snakes, key = { it.id }) { snake ->
                    SnakeRow(snake = snake, onClick = { onSnakeClick(snake.id) })
                }
            }
        }
    }
}

@Composable
private fun SnakeRow(snake: Snake, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(snake.name, style = MaterialTheme.typography.titleMedium)
            val subtitle = listOf(snake.species, snake.morph).filter { it.isNotBlank() }.joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
