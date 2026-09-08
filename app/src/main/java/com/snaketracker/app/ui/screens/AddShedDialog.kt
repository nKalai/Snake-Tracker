package com.snaketracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snaketracker.app.data.entities.ShedEvent

@Composable
fun AddShedDialog(
    snakeId: Long,
    onDismiss: () -> Unit,
    onSave: (ShedEvent) -> Unit
) {
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var complete by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Shed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(label = "Date", dateMillis = date, onDateSelected = { date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Complete shed", modifier = Modifier.weight(1f))
                    Switch(checked = complete, onCheckedChange = { complete = it })
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (e.g. stuck cap, retained eye caps)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(ShedEvent(snakeId = snakeId, date = date, complete = complete, notes = notes.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
