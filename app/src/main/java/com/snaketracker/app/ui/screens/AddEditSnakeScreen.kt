package com.snaketracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.ui.viewmodel.SnakeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSnakeScreen(
    viewModel: SnakeViewModel,
    snakeId: Long?, // null = adding new snake
    onDone: () -> Unit
) {
    val existing by if (snakeId != null) viewModel.snake(snakeId).collectAsStateWithLifecycle(initialValue = null)
        else remember { mutableStateOf<Snake?>(null) }

    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("") }
    var morph by remember { mutableStateOf("") }
    var enclosure by remember { mutableStateOf("") }
    var feedingIntervalDays by remember { mutableStateOf("7") }
    var remindersEnabled by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (existing != null && !initialized) {
            name = existing!!.name
            species = existing!!.species
            morph = existing!!.morph
            enclosure = existing!!.enclosure
            feedingIntervalDays = existing!!.feedingIntervalDays.toString()
            remindersEnabled = existing!!.remindersEnabled
            notes = existing!!.notes
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (snakeId == null) "Add Snake" else "Edit Snake") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name*") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = species, onValueChange = { species = it }, label = { Text("Species") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = morph, onValueChange = { morph = it }, label = { Text("Morph") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = enclosure, onValueChange = { enclosure = it }, label = { Text("Enclosure / Tank ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = feedingIntervalDays,
                onValueChange = { feedingIntervalDays = it.filter { c -> c.isDigit() } },
                label = { Text("Feeding reminder interval (days)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Enable feeding reminders", modifier = Modifier.weight(1f))
                Switch(checked = remindersEnabled, onCheckedChange = { remindersEnabled = it })
            }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(), minLines = 3
            )

            Button(
                onClick = {
                    val interval = feedingIntervalDays.toIntOrNull() ?: 7
                    if (snakeId == null) {
                        viewModel.addSnake(
                            Snake(
                                name = name.trim(),
                                species = species.trim(),
                                morph = morph.trim(),
                                enclosure = enclosure.trim(),
                                feedingIntervalDays = interval,
                                remindersEnabled = remindersEnabled,
                                notes = notes.trim()
                            )
                        )
                    } else {
                        existing?.let {
                            viewModel.updateSnake(
                                it.copy(
                                    name = name.trim(),
                                    species = species.trim(),
                                    morph = morph.trim(),
                                    enclosure = enclosure.trim(),
                                    feedingIntervalDays = interval,
                                    remindersEnabled = remindersEnabled,
                                    notes = notes.trim()
                                )
                            )
                        }
                    }
                    onDone()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
